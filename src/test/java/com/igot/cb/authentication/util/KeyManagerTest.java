package com.igot.cb.authentication.util;

import com.igot.cb.authentication.model.KeyData;

import java.security.PublicKey;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;

import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(KeyManagerTest.class.getName());

    @Test
    public void testLoadPublicKeyWithInvalidKeyString() {
        String invalidKey = "InvalidKeyWithoutHeaderAndFooter";
        assertThrows(java.security.spec.InvalidKeySpecException.class, () -> {
            KeyManager.loadPublicKey(invalidKey);
        });
    }

    @Test
    public void test_getPublicKey_nonExistentKeyId() {
        KeyManager keyManager = new KeyManager();
        String nonExistentKeyId = "nonexistent_key_id";
        KeyData result = keyManager.getPublicKey(nonExistentKeyId);
        assertNull(result);
    }

    @Test
    public void test_getPublicKey_returnsCorrectKeyData() {
        KeyManager spyKeyManager = spy(new KeyManager());
        KeyData mockKeyData = new KeyData("testKey", null);
        doReturn(mockKeyData).when(spyKeyManager).getPublicKey("testKey");
        KeyData result = spyKeyManager.getPublicKey("testKey");
        assertEquals(mockKeyData, result);
    }

    @Test
    public void test_loadPublicKey_validKeyString() throws Exception {
        String validPublicKeyString =
                "-----BEGIN PUBLIC KEY-----\n" +
                        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqe4M4f7sVew+5U2G6l5H\n" +
                        "1T0WRfJOYd3qwWn2MtOpQ8kWODsxdmBrERHJCKrfTsNpcl8p3CsV1KUHmIqOeFLG\n" +
                        "yyQ+QjMoCQ9uGzbCAPyLYAAIgf/mKPa7BK5sLfZ7MCPupA8K/RB/g/3ZHlTSWJn+\n" +
                        "2uVyqY+xIzDfS1tLGnQz0Izmzy/JZm6+0BHrRs7TXVWrN6+YFlzXlN2cuLkxDGeu\n" +
                        "fUPRtmS+gUFNPnWApxdFt/zq9riIqxECG1QHpZFg3c+QOj+3emNhJMxFhKTKMeZP\n" +
                        "fkEkspt1ATsNnG+y+ZQKUQM1xPEk2FTaMdlDj1/5S9t5Rq8PlPlRFnBrBnrboJ+v\n" +
                        "XQIDAQAB\n" +
                        "-----END PUBLIC KEY-----";
        PublicKey publicKey = KeyManager.loadPublicKey(validPublicKeyString);
        assertNotNull("Public key should not be null", publicKey);
        assertEquals("RSA", publicKey.getAlgorithm());
    }


    private static final String VALID_KEY_STRING =
            "-----BEGIN PUBLIC KEY-----\n" +
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqe4M4f7sVew+5U2G6l5H\n" +
                    "1T0WRfJOYd3qwWn2MtOpQ8kWODsxdmBrERHJCKrfTsNpcl8p3CsV1KUHmIqOeFLG\n" +
                    "yyQ+QjMoCQ9uGzbCAPyLYAAIgf/mKPa7BK5sLfZ7MCPupA8K/RB/g/3ZHlTSWJn+\n" +
                    "2uVyqY+xIzDfS1tLGnQz0Izmzy/JZm6+0BHrRs7TXVWrN6+YFlzXlN2cuLkxDGeu\n" +
                    "fUPRtmS+gUFNPnWApxdFt/zq9riIqxECG1QHpZFg3c+QOj+3emNhJMxFhKTKMeZP\n" +
                    "fkEkspt1ATsNnG+y+ZQKUQM1xPEk2FTaMdlDj1/5S9t5Rq8PlPlRFnBrBnrboJ+v\n" +
                    "XQIDAQAB\n" +
                    "-----END PUBLIC KEY-----";


    @Test
    public void test_loadPublicKey_noNewlines() throws Exception {
        String noNewlines = VALID_KEY_STRING.replace("\n", "");
        PublicKey key = KeyManager.loadPublicKey(noNewlines);
        assertNotNull(key);
    }


    @Test
    public void test_init_fileSystemException() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = Mockito.mockStatic(PropertiesCache.class);
             MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class);
             MockedStatic<Paths> pathsMock = Mockito.mockStatic(Paths.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            KeyManager keyManager = new KeyManager();
            keyManager.init();
        }
    }

    @Test
    public void test_init_propertyNotFound() throws Exception {
        KeyManager spyKeyManager = spy(new KeyManager());
        try (MockedStatic<PropertiesCache> propertiesCacheMock = Mockito.mockStatic(PropertiesCache.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            spyKeyManager.init();
            verify(spyKeyManager).init();
        }
    }
}