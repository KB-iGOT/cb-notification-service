package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.elasticsearch.service.EsClientService;
import com.igot.cb.notification.service.FormExpiryValidator;
import com.igot.cb.transactional.caffeinecache.NotificationMetadataCacheManager;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Drives a PENDING → EXPIRED state transition for notification records whose form has passed its
 * {@code endDate}. Processes in configurable batches with Caffeine cache-first ES lookups.
 */

@Service
@Slf4j
public class FormExpiryValidatorImpl implements FormExpiryValidator {

    private final EsClientService esClientService;
    private final CbServerProperties cbServerProperties;
    private final ObjectMapper objectMapper;
    private final NotificationMetadataCacheManager cacheManager;

    public FormExpiryValidatorImpl(EsClientService esClientService,
                                   CbServerProperties cbServerProperties,
                                   ObjectMapper objectMapper,
                                   NotificationMetadataCacheManager cacheManager) {
        this.esClientService = esClientService;
        this.cbServerProperties = cbServerProperties;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    /** Filters to PENDING records, processes in fixed-size batches, and returns the original list with statuses applied in-place. */
    @Override
    public List<Map<String, Object>> validateAndMarkExpired(List<Map<String, Object>> records) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> pendingRecords = filterPendingRecords(records);
        if (CollectionUtils.isEmpty(pendingRecords)) {
            log.info("No PENDING records found among {} total — skipping expiry check", records.size());
            return records;
        }
        int batchSize = cbServerProperties.getFormsEsFetchBatchSize();
        log.info("Starting expiry validation: {} PENDING record(s) out of {} total, batch size {}",
                pendingRecords.size(), records.size(), batchSize);
        for (int i = 0; i < pendingRecords.size(); i += batchSize) {
            List<Map<String, Object>> batch = pendingRecords.subList(i, Math.min(i + batchSize, pendingRecords.size()));
            processBatch(batch);
        }
        log.info("Expiry validation complete for {} PENDING record(s)", pendingRecords.size());
        return records;
    }

    /** Indexes batch by formId, resolves endDates via cache/ES, then marks expired records. Short-circuits if no valid formId found. */
    private void processBatch(List<Map<String, Object>> batch) {
        Map<String, List<Map<String, Object>>> formIdToRecords = buildFormIdToRecordMap(batch);
        if (formIdToRecords.isEmpty()) {
            log.debug("Batch of {} record(s) has no extractable formIds — skipping", batch.size());
            return;
        }
        log.info("Processing batch: {} record(s), {} unique formId(s)", batch.size(), formIdToRecords.size());
        List<String> formIds = List.copyOf(formIdToRecords.keySet());
        Map<String, Long> formIdToEndDate = resolveEndDates(formIds);
        markExpiredRecords(formIdToRecords, formIdToEndDate);
    }

    /** Parses each notification's metadata once and groups all notifications by formId; drops entries with invalid or missing formIds. */
    private Map<String, List<Map<String, Object>>> buildFormIdToRecordMap(List<Map<String, Object>> batch) {
        return batch.stream()
                .flatMap(notification -> Optional.ofNullable(extractFormId(notification))
                        .filter(id -> !id.isBlank())
                        .map(id -> Map.entry(id, notification))
                        .stream())
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    /**
     * Resolves endDates for all formIds using the Caffeine cache first.
     * Only formIds not found in the cache are forwarded to Elasticsearch,
     * and their results are stored back into the cache for future calls.
     */
    private Map<String, Long> resolveEndDates(List<String> formIds) {
        Map<String, Optional<Long>> cacheResults = formIds.stream()
                .collect(Collectors.toMap(id -> id, cacheManager::get));
        Map<String, Long> hits = cacheResults.entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElseThrow()));
        List<String> misses = cacheResults.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        log.info("Cache resolution: {} hit(s), {} miss(es) out of {} formId(s)",
                hits.size(), misses.size(), formIds.size());
        if (misses.isEmpty()) {
            return hits;
        }
        log.info("Querying Elasticsearch for {} uncached formId(s)", misses.size());
        List<Map<String, Object>> esResults = fetchFormMetadata(misses);
        log.info("Elasticsearch returned {} document(s) for {} queried formId(s)",
                esResults.size(), misses.size());
        Map<String, Long> esEndDates = buildFormIdToEndDateMap(esResults);
        esEndDates.forEach(cacheManager::put);

        Map<String, Long> result = new HashMap<>(hits);
        result.putAll(esEndDates);
        return result;
    }

    /** Terms query to Elasticsearch for the given formIds using config-driven index alias, context type, and field projection. */
    private List<Map<String, Object>> fetchFormMetadata(List<String> formIds) {
        return esClientService.searchByTerms(
                cbServerProperties.getFormsEsIndexAlias(),
                Constants.FORM_ID,
                formIds,
                cbServerProperties.getFormsEsContextType(),
                cbServerProperties.getFormsEsFetchFields()
        );
    }

    /** Sets STATUS=EXPIRED on all notifications whose form endDate is in the past; all notifications sharing an expired formId are marked. */
    private void markExpiredRecords(Map<String, List<Map<String, Object>>> formIdToRecords,
                                    Map<String, Long> formIdToEndDate) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> expired = formIdToRecords.entrySet().stream()
                .filter(entry -> isFormExpired(formIdToEndDate.get(entry.getKey()), now))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        expired.forEach(notification -> notification.put(Constants.STATUS, Constants.STATUS_EXPIRED));
        if (expired.isEmpty()) {
            log.info("No expired records found in this batch of {} formId(s)", formIdToRecords.size());
        } else {
            log.info("Marked {} of {} record(s) as {} in this batch",
                    expired.size(), formIdToRecords.size(), Constants.STATUS_EXPIRED);
        }
    }

    /** Returns {@code true} if {@code endDate} is non-null and strictly before {@code now}; null endDate is treated as not expired. */
    private boolean isFormExpired(Long endDate, long now) {
        return endDate != null && endDate < now;
    }

    /** Parses the JSON {@code metadata} field to extract {@code formId}; returns {@code null} on absent, blank, or malformed input. */
    private String extractFormId(Map<String, Object> notification) {
        if (!(notification.get(Constants.METADATA) instanceof String metaStr) || metaStr.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> metaMap = objectMapper.readValue(metaStr, new TypeReference<>() {});
            if (metaMap.get(Constants.FORM_ID) instanceof String formId) {
                return formId;
            }
        } catch (Exception e) {
            log.warn("Failed to parse metadata for notification [{}]", notification.get(Constants.NOTIFICATION_ID));
        }
        return null;
    }

    /** Filters records to those with PENDING status (case-insensitive). */
    private List<Map<String, Object>> filterPendingRecords(List<Map<String, Object>> records) {
        return records.stream()
                .filter(r -> r.get(Constants.STATUS) instanceof String status
                        && Constants.STATUS_PENDING.equalsIgnoreCase(status))
                .toList();
    }

    /** Builds a formId → endDate map from ES documents; tolerates missing formIds and absent endDates without error. */
    private Map<String, Long> buildFormIdToEndDateMap(List<Map<String, Object>> esResults) {
        if (CollectionUtils.isEmpty(esResults)) {
            return Collections.emptyMap();
        }
        return esResults.stream()
                .flatMap(esDoc -> Optional.of(esDoc)
                        .filter(doc -> doc.get(Constants.FORM_ID) instanceof String id && !id.isBlank())
                        .flatMap(this::extractEndDate)
                        .map(endDate -> Map.entry((String) esDoc.get(Constants.FORM_ID), endDate))
                        .stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Checks top-level {@code endDate} first, then {@code additionalProperties.endDate}; returns empty if neither yields a Number. */
    private Optional<Long> extractEndDate(Map<String, Object> esDoc) {
        if (esDoc.get(Constants.END_DATE) instanceof Number topLevel) {
            return Optional.of(topLevel.longValue());
        }
        if (esDoc.get(Constants.ADDITIONAL_PROPERTIES) instanceof Map<?, ?> apMap
                && apMap.get(Constants.END_DATE) instanceof Number nested) {
            return Optional.of(nested.longValue());
        }
        return Optional.empty();
    }
}
