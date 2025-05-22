package com.igot.cb.authentication.model;

import org.junit.Test;
import static org.junit.Assert.*;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class KeyDataTest {

    /**
     * Tests the constructor of KeyData and verifies that the getter methods
     * correctly return the values provided during initialization.
     * This test creates a KeyData instance with a sample key ID and a generated
     * public key, then validates that the getters return the expected values.
     *
     * @throws NoSuchAlgorithmException if the RSA algorithm is not available
     */
    @Test
    public void testConstructorAndGetters() throws NoSuchAlgorithmException {
        String keyId = "test-key-id";
        PublicKey publicKey = generatePublicKey();
        KeyData keyData = new KeyData(keyId, publicKey);
        assertEquals(keyId, keyData.getKeyId());
        assertEquals(publicKey, keyData.getPublicKey());
    }

    @Test
    public void testSetters() throws NoSuchAlgorithmException {
        String initialKeyId = "initial-key-id";
        PublicKey initialPublicKey = generatePublicKey();
        KeyData keyData = new KeyData(initialKeyId, initialPublicKey);
        String newKeyId = "new-key-id";
        PublicKey newPublicKey = generatePublicKey();
        keyData.setKeyId(newKeyId);
        keyData.setPublicKey(newPublicKey);
        assertEquals("KeyId should be updated", newKeyId, keyData.getKeyId());
        assertEquals("PublicKey should be updated", newPublicKey, keyData.getPublicKey());
    }

    @Test
    public void testNullValues() {
        KeyData keyData = new KeyData(null, null);
        assertNull("KeyId should be null", keyData.getKeyId());
        assertNull("PublicKey should be null", keyData.getPublicKey());
    }

    /**
     * Helper method to generate a public key for testing
     */
    private PublicKey generatePublicKey() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair().getPublic();
    }
}