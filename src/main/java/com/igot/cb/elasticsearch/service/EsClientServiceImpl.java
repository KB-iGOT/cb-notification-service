package com.igot.cb.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link EsClientService} backed by the Elasticsearch Java client (8.x).
 * Uses bool-filter queries for score-free, cache-friendly lookups.
 */
@Service
@Slf4j
public class EsClientServiceImpl implements EsClientService {

    private static final Class<Map<String, Object>> SOURCE_TYPE =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final ElasticsearchClient elasticsearchClient;

    @Value("${es.max.terms.count}")
    private int maxTermsCount;

    @Value("${es.query.timeout}")
    private String queryTimeout;

    public EsClientServiceImpl(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /** Bool-filter terms query on {@code fieldName}; optionally narrows by {@code contextType}; projects only requested {@code fields}. */
    @Override
    public List<Map<String, Object>> searchByTerms(
            String esIndexName, String fieldName, List<String> values,
            String contextType, List<String> fields) {
        if (!isValidInput(esIndexName, fieldName, values)) {
            log.debug("searchByTerms skipped — invalid input: index='{}', field='{}', valueCount={}",
                    esIndexName, fieldName, CollectionUtils.isEmpty(values) ? 0 : values.size());
            return Collections.emptyList();
        }
        if (values.size() > maxTermsCount) {
            log.error("searchByTerms aborted — {} value(s) exceeds ES max terms limit of {}", values.size(), maxTermsCount);
            return Collections.emptyList();
        }
        try {
            SearchRequest request = buildSearchRequest(esIndexName, fieldName, values, contextType, fields);
            List<Map<String, Object>> results = extractSources(elasticsearchClient.search(request, SOURCE_TYPE));
            log.info("searchByTerms: index='{}', field='{}', queried={}, returned={}",
                    esIndexName, fieldName, values.size(), results.size());
            return results;
        } catch (IOException e) {
            log.error("searchByTerms failed — index='{}', field='{}': {}", esIndexName, fieldName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /** Returns {@code true} only when index, field, and values are all non-null and non-empty. */
    private boolean isValidInput(String esIndexName, String fieldName, List<String> values) {
        return StringUtils.isNotBlank(esIndexName)
                && StringUtils.isNotBlank(fieldName)
                && CollectionUtils.isNotEmpty(values);
    }

    /** Builds a bool-filter {@link SearchRequest} with size, timeout, terms clause, optional contextType clause, and source field projection. */
    private SearchRequest buildSearchRequest(
            String esIndexName, String fieldName, List<String> values,
            String contextType, List<String> fields) {
        return SearchRequest.of(b -> {
            b.index(esIndexName)
             .size(values.size())
             .timeout(queryTimeout)
             .query(q -> q.bool(bool -> {
                bool.filter(f -> f.terms(t -> t.field(fieldName)
                        .terms(tv -> tv.value(toFieldValues(values)))));
                if (StringUtils.isNotBlank(contextType)) {
                    bool.filter(f -> f.term(t -> t.field(Constants.CONTEXT_TYPE).value(contextType)));
                }
                return bool;
            }));
            if (CollectionUtils.isNotEmpty(fields)) {
                b.source(s -> s.filter(f -> f.includes(fields)));
            }
            return b;
        });
    }

    /** Converts plain strings to {@link FieldValue} instances required by the ES terms query API. */
    private List<FieldValue> toFieldValues(List<String> values) {
        return values.stream().map(FieldValue::of).toList();
    }

    /** Extracts non-null source maps from all hits in the search response. */
    private List<Map<String, Object>> extractSources(
            SearchResponse<Map<String, Object>> response) {
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }
}

