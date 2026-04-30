package com.igot.cb.transactional.caffeinecache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.igot.cb.util.CbServerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed in-process cache mapping form IDs to their {@code endDate}
 * epoch-millis. This is a pure read-through store — callers are responsible
 * for fetching from Elasticsearch on a miss and warming the cache via {@link #put}.
 *
 * <p>TTL and maximum capacity are controlled by {@code forms.cache.ttl.seconds}
 * and {@code forms.cache.max.size} in {@code application.properties}.</p>
 */
@Service
@Slf4j
public class NotificationMetadataCacheManager {

    private final CbServerProperties cbServerProperties;

    /** Key: formId, Value: endDate epoch-millis. */
    private Cache<String, Long> cache;

    public NotificationMetadataCacheManager(CbServerProperties cbServerProperties) {
        this.cbServerProperties = cbServerProperties;
    }

    /** Builds the Caffeine cache once config-driven TTL and max-size values are available. */
    @PostConstruct
    public void initCache() {
        cache = Caffeine.newBuilder()
                .maximumSize(cbServerProperties.getFormsCacheMaxSize())
                .expireAfterWrite(cbServerProperties.getFormsCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
        log.info("NotificationMetadataCacheManager initialised — TTL={}s, maxSize={}",
                cbServerProperties.getFormsCacheTtlSeconds(),
                cbServerProperties.getFormsCacheMaxSize());
    }

    /**
     * Returns the cached {@code endDate} for {@code formId}, or {@link Optional#empty()}
     * on a cache miss. Callers are responsible for querying Elasticsearch on a miss
     * and warming the cache via {@link #put}.
     */
    public Optional<Long> get(String formId) {
        Long cached = cache.getIfPresent(formId);
        if (cached != null) {
            log.debug("Cache hit for formId={}", formId);
            return Optional.of(cached);
        }
        log.debug("Cache miss for formId={}", formId);
        return Optional.empty();
    }

    /** Warms the cache explicitly; useful for bulk callers that already hold ES results. */
    public void put(String formId, Long endDate) {
        cache.put(formId, endDate);
        log.info("Cache warmed: formId={}, endDate={}", formId, endDate);
    }

    /** Evicts {@code formId} so the next {@link #get} call reloads from Elasticsearch. */
    public void invalidate(String formId) {
        cache.invalidate(formId);
        log.debug("Cache entry invalidated for formId={}", formId);
    }

    /** Clears all entries; all subsequent {@link #get} calls will return empty until re-warmed. */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("NotificationMetadataCacheManager: full cache invalidation");
    }
}
