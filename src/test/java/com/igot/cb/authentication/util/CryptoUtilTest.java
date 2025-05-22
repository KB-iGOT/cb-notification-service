package com.igot.cb.authentication.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

import com.igot.cb.util.Constants;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class CryptoUtilTest {

    /**
     * Tests the verifyRSASign method with an invalid algorithm.
     * This test expects the method to return false when an unsupported algorithm is provided.
     */
    @Test
    public void testVerifyRSASignWithInvalidAlgorithm() {
        String payLoad = "Test payload";
        byte[] signature = new byte[]{1, 2, 3, 4, 5};
        PublicKey key = null; // We don't need a real key for this test
        String invalidAlgorithm = "InvalidAlgorithm";
        boolean result = CryptoUtil.verifyRSASign(payLoad, signature, key, invalidAlgorithm);
        assertFalse("verifyRSASign should return false for an invalid algorithm", result);
    }

    /**
     * Tests the verifyRSASign method with an invalid public key.
     * This test expects the method to return false when an invalid key is provided.
     */
    @Test
    public void testVerifyRSASignWithInvalidKey() {
        String payLoad = "Test payload";
        byte[] signature = new byte[]{1, 2, 3, 4, 5};
        PublicKey invalidKey = null;
        String algorithm = "SHA256withRSA";
        boolean result = CryptoUtil.verifyRSASign(payLoad, signature, invalidKey, algorithm);
        assertFalse("verifyRSASign should return false for an invalid key", result);
    }

    /**
     * Tests the verifyRSASign method with valid RSA signature.
     * This test creates a valid RSA signature for a payload and verifies it using the CryptoUtil.verifyRSASign method.
     * It expects the method to return true, indicating a successful verification.
     */
    @Test
    public void test_verifyRSASign_withValidSignature() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        String payload = "Test payload";
        String algorithm = "SHA256withRSA";
        Signature privateSignature = Signature.getInstance(algorithm);
        privateSignature.initSign(keyPair.getPrivate());
        privateSignature.update(payload.getBytes("ASCII"));
        byte[] signature = privateSignature.sign();
        boolean result = CryptoUtil.verifyRSASign(payload, signature, publicKey, algorithm);
        assertTrue(result);
    }

    @Test
    public void test_verifyRSASign_coverage() throws Exception {
        String payload = "header.payload";
        byte[] signature = "test-signature".getBytes();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        Signature privateSignature = Signature.getInstance(Constants.SHA_256_WITH_RSA);
        privateSignature.initSign(keyPair.getPrivate());
        privateSignature.update(payload.getBytes(StandardCharsets.US_ASCII));
        byte[] realSignature = privateSignature.sign();
        boolean validResult = CryptoUtil.verifyRSASign(
                payload,
                realSignature,
                publicKey,
                Constants.SHA_256_WITH_RSA
        );
        assertTrue("Valid signature should verify correctly", validResult);
        boolean invalidResult = CryptoUtil.verifyRSASign(
                payload,
                signature,
                publicKey,
                Constants.SHA_256_WITH_RSA
        );
        assertFalse("Invalid signature should fail verification", invalidResult);
        boolean nullKeyResult = CryptoUtil.verifyRSASign(
                payload,
                realSignature,
                null,
                Constants.SHA_256_WITH_RSA
        );
        assertFalse("Null key should fail verification", nullKeyResult);
        boolean invalidAlgoResult = CryptoUtil.verifyRSASign(
                payload,
                realSignature,
                publicKey,
                "InvalidAlgorithm"
        );
        assertFalse("Invalid algorithm should fail verification", invalidAlgoResult);
    }

}
