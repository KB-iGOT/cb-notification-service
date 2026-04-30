package com.igot.cb.transactional.caffeinecache;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMetadataCacheManagerTest {

    @Mock
    private CbServerProperties cbServerProperties;

    private NotificationMetadataCacheManager cacheManager;

    private static final String FORM_ID = "form-001";
    private static final long END_DATE_MILLIS = System.currentTimeMillis() + 86_400_000L;

    @BeforeEach
    void setUp() {
        when(cbServerProperties.getFormsCacheTtlSeconds()).thenReturn(300L);
        when(cbServerProperties.getFormsCacheMaxSize()).thenReturn(100L);
        cacheManager = new NotificationMetadataCacheManager(cbServerProperties);
        cacheManager.initCache();
    }

    @Test
    void shouldReturnEmptyOnCacheMiss() {
        Optional<Long> result = cacheManager.get(FORM_ID);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnValueAfterExplicitPut() {
        cacheManager.put(FORM_ID, END_DATE_MILLIS);
        Optional<Long> result = cacheManager.get(FORM_ID);
        assertTrue(result.isPresent());
        assertEquals(END_DATE_MILLIS, result.get());
    }

    @Test
    void shouldReturnCachedValueOnSubsequentGet() {
        cacheManager.put(FORM_ID, END_DATE_MILLIS);
        assertEquals(Optional.of(END_DATE_MILLIS), cacheManager.get(FORM_ID));
        assertEquals(Optional.of(END_DATE_MILLIS), cacheManager.get(FORM_ID));
    }

    @Test
    void shouldReturnEmptyAfterInvalidatingSingleKey() {
        cacheManager.put(FORM_ID, END_DATE_MILLIS);
        cacheManager.invalidate(FORM_ID);
        assertTrue(cacheManager.get(FORM_ID).isEmpty());
    }

    @Test
    void shouldReturnEmptyAfterInvalidateAll() {
        cacheManager.put(FORM_ID, END_DATE_MILLIS);
        cacheManager.put("form-002", END_DATE_MILLIS);
        cacheManager.invalidateAll();
        assertTrue(cacheManager.get(FORM_ID).isEmpty());
        assertTrue(cacheManager.get("form-002").isEmpty());
    }

    @Test
    void shouldReturnEmptyForKeyNeverPut() {
        cacheManager.put("other-form", END_DATE_MILLIS);
        assertTrue(cacheManager.get(FORM_ID).isEmpty());
    }

    @Test
    void shouldOverwriteExistingValueOnReput() {
        long newEndDate = END_DATE_MILLIS + 10_000L;
        cacheManager.put(FORM_ID, END_DATE_MILLIS);
        cacheManager.put(FORM_ID, newEndDate);
        assertEquals(Optional.of(newEndDate), cacheManager.get(FORM_ID));
    }
}
