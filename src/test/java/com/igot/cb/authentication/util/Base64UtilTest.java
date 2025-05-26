package com.igot.cb.authentication.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class Base64UtilTest {

    @Test
    public void testEncodeAndDecodeDefaultMode() {
        String original = "Hello, World!";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testEncodeToStringAndDecode() {
        String original = "Test string with special chars: !@#$%^&*()";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedString = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encodedString, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testEncodeWithWebSafeFlag() {
        String original = "This+will/be=encoded~differently";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedWebSafe = Base64Util.encodeToString(originalBytes, Base64Util.URL_SAFE);
        assertNotEquals(encodedDefault, encodedWebSafe);
        byte[] decodedWebSafe = Base64Util.decode(encodedWebSafe, Base64Util.URL_SAFE);
        assertEquals(original, new String(decodedWebSafe, StandardCharsets.UTF_8));
    }

    @Test
    public void testDecodeWithWebSafeFlag() {
        String original = "Web-safe_encoded+string/with=chars";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedWebSafe = Base64Util.encodeToString(originalBytes, Base64Util.URL_SAFE);
        byte[] decodedWebSafe = Base64Util.decode(encodedWebSafe, Base64Util.URL_SAFE);
        assertEquals(original, new String(decodedWebSafe, StandardCharsets.UTF_8));
    }

    @Test
    public void testEncodeWithNoPadding() {
        String original = "A";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedNoPadding = Base64Util.encodeToString(originalBytes, Base64Util.NO_PADDING);
        assertTrue("Default encoding should have padding", encodedDefault.contains("=="));
        assertFalse("No padding encoding should not have padding", encodedNoPadding.contains("="));
    }

    @Test
    public void testEncodeWithNoWrap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Long string ");
        }
        String longString = sb.toString();
        byte[] longBytes = longString.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(longBytes, Base64Util.DEFAULT);
        String encodedNoWrap = Base64Util.encodeToString(longBytes, Base64Util.NO_WRAP);
        assertTrue(encodedDefault.contains("\n"));
        assertFalse(encodedNoWrap.contains("\n"));
    }

    @Test
    public void testEncodeWithCRLF() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Long string ");
        }
        String longString = sb.toString();
        byte[] longBytes = longString.getBytes(StandardCharsets.UTF_8);
        String encodedDefault = Base64Util.encodeToString(longBytes, Base64Util.DEFAULT);
        String encodedCRLF = Base64Util.encodeToString(longBytes, Base64Util.CRLF);
        assertFalse(encodedDefault.contains("\r\n"));
        assertTrue(encodedCRLF.contains("\r\n"));
    }

    @Test
    public void testDecodeInvalidInput() {
        String invalidBase64 = "===="; // Invalid Base64 format
        try {
            byte[] result = Base64Util.decode(invalidBase64, Base64Util.DEFAULT);
            assertNotNull(result);
            assertEquals(0, result.length);
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testEncodeDecode_EmptyString() {
        String empty = "";
        byte[] emptyBytes = empty.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(emptyBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(0, decoded.length);
        assertEquals(empty, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testEncodeDecode_BinaryData() {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte)i;
        }
        byte[] encoded = Base64Util.encode(binaryData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(binaryData, decoded);
    }

    @Test
    public void testEncodeWithOffset() {
        byte[] data = "HelloWorld".getBytes(StandardCharsets.UTF_8);
        byte[] encodedFull = Base64Util.encode(data, Base64Util.DEFAULT);
        byte[] encodedPartial = Base64Util.encode(data, 5, 5, Base64Util.DEFAULT);
        assertNotEquals(new String(encodedFull), new String(encodedPartial));
        assertEquals("World", new String(Base64Util.decode(encodedPartial, Base64Util.DEFAULT), StandardCharsets.UTF_8));
    }

    @Test
    public void testBasicEncodeDecode() {
        String original = "Hello World";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Util.encodeToString(bytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testEncodeWithOffsetAndLength() {
        byte[] input = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.UTF_8);
        int offset = 5;
        int len = 10;
        String encoded = Base64Util.encodeToString(input, offset, len, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        byte[] expected = Arrays.copyOfRange(input, offset, offset + len);
        assertArrayEquals(expected, decoded);
    }

    @Test
    public void testUrlSafeEncoding() {
        byte[] bytesForPlus = {(byte)0xF8, (byte)0xBF};  // These encode to "+/"
        String regularEncoded = Base64Util.encodeToString(bytesForPlus, Base64Util.DEFAULT);
        assertTrue("Regular encoding should contain +", regularEncoded.contains("+"));
        byte[] bytesForSlash = {(byte)0xBF, (byte)0xF0};  // These encode to "v/"
        String regularEncodedSlash = Base64Util.encodeToString(bytesForSlash, Base64Util.DEFAULT);
        assertTrue("Regular encoding should contain /", regularEncodedSlash.contains("/"));
        String urlSafeEncodedPlus = Base64Util.encodeToString(bytesForPlus, Base64Util.URL_SAFE);
        String urlSafeEncodedSlash = Base64Util.encodeToString(bytesForSlash, Base64Util.URL_SAFE);
        assertFalse("URL-safe encoding should not contain +", urlSafeEncodedPlus.contains("+"));
        assertTrue("URL-safe encoding should contain - instead of +", urlSafeEncodedPlus.contains("-"));
        assertFalse("URL-safe encoding should not contain /", urlSafeEncodedSlash.contains("/"));
        assertTrue("URL-safe encoding should contain _ instead of /", urlSafeEncodedSlash.contains("_"));
        byte[] decodedPlus = Base64Util.decode(urlSafeEncodedPlus, Base64Util.URL_SAFE);
        byte[] decodedSlash = Base64Util.decode(urlSafeEncodedSlash, Base64Util.URL_SAFE);
        assertArrayEquals(bytesForPlus, decodedPlus);
        assertArrayEquals(bytesForSlash, decodedSlash);
    }

    @Test
    public void testNoPaddingEncoding() {
        byte[] oneByteData = {65}; // 'A'
        byte[] twoByteData = {65, 66}; // 'AB'
        String encodedWithPadding = Base64Util.encodeToString(oneByteData, Base64Util.DEFAULT);
        assertTrue("Default encoding should have padding", encodedWithPadding.contains("="));
        String encodedNoPadding = Base64Util.encodeToString(oneByteData, Base64Util.NO_PADDING);
        assertFalse("No padding encoding should not have padding", encodedNoPadding.contains("="));
        byte[] decodedWithPadding = Base64Util.decode(encodedWithPadding, Base64Util.DEFAULT);
        byte[] decodedNoPadding = Base64Util.decode(encodedNoPadding, Base64Util.NO_PADDING);
        assertArrayEquals(oneByteData, decodedWithPadding);
        assertArrayEquals(oneByteData, decodedNoPadding);
        String encodedTwoBytesNoPadding = Base64Util.encodeToString(twoByteData, Base64Util.NO_PADDING);
        assertFalse(encodedTwoBytesNoPadding.contains("="));
        byte[] decodedTwoBytes = Base64Util.decode(encodedTwoBytesNoPadding, Base64Util.NO_PADDING);
        assertArrayEquals(twoByteData, decodedTwoBytes);
    }

    @Test
    public void testNoWrapEncoding() {
        byte[] longData = new byte[200];
        Arrays.fill(longData, (byte)'A');
        String encodedWithWrap = Base64Util.encodeToString(longData, Base64Util.DEFAULT);
        assertTrue(encodedWithWrap.contains("\n"));
        String encodedNoWrap = Base64Util.encodeToString(longData, Base64Util.NO_WRAP);
        assertFalse(encodedNoWrap.contains("\n"));
        byte[] decodedWithWrap = Base64Util.decode(encodedWithWrap, Base64Util.DEFAULT);
        byte[] decodedNoWrap = Base64Util.decode(encodedNoWrap, Base64Util.NO_WRAP);
        assertArrayEquals(longData, decodedWithWrap);
        assertArrayEquals(longData, decodedNoWrap);
    }

    @Test
    public void testCrlfLineBreaks() {
        byte[] longData = new byte[200];
        Arrays.fill(longData, (byte)'A');
        String encodedWithCrlf = Base64Util.encodeToString(longData, Base64Util.CRLF);
        assertTrue(encodedWithCrlf.contains("\r\n"));
        String encodedWithLf = Base64Util.encodeToString(longData, Base64Util.DEFAULT);
        assertTrue(encodedWithLf.contains("\n"));
        assertFalse(encodedWithLf.contains("\r\n"));
        byte[] decodedWithCrlf = Base64Util.decode(encodedWithCrlf, Base64Util.CRLF);
        byte[] decodedWithLf = Base64Util.decode(encodedWithLf, Base64Util.DEFAULT);
        assertArrayEquals(longData, decodedWithCrlf);
        assertArrayEquals(longData, decodedWithLf);
    }

    @Test
    public void testEmptyInput() {
        byte[] emptyArray = new byte[0];
        String encoded = Base64Util.encodeToString(emptyArray, Base64Util.DEFAULT);
        assertEquals("", encoded);
        byte[] decoded = Base64Util.decode("", Base64Util.DEFAULT);
        assertEquals(0, decoded.length);
    }

    @Test
    public void testEncodeDecodeWithFlags() {
        String original = "Test with all flags";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        int[] flags = {
                Base64Util.DEFAULT,
                Base64Util.NO_PADDING,
                Base64Util.NO_WRAP,
                Base64Util.CRLF,
                Base64Util.URL_SAFE,
                Base64Util.NO_PADDING | Base64Util.NO_WRAP,
                Base64Util.NO_PADDING | Base64Util.URL_SAFE,
                Base64Util.NO_WRAP | Base64Util.URL_SAFE,
                Base64Util.NO_PADDING | Base64Util.NO_WRAP | Base64Util.URL_SAFE
        };
        for (int flag : flags) {
            String encoded = Base64Util.encodeToString(originalBytes, flag);
            byte[] decoded = Base64Util.decode(encoded, flag);
            assertEquals("Failed with flag: " + flag, original, new String(decoded, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testDecodeWithWhitespace() {
        String original = "Hello World";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Util.encodeToString(originalBytes, Base64Util.DEFAULT);
        String encodedWithSpaces = encoded.charAt(0) + " " + encoded.substring(1);
        String encodedWithTabs = encoded.charAt(0) + "\t" + encoded.substring(1);
        String encodedWithNewlines = encoded.charAt(0) + "\n" + encoded.substring(1);
        String encodedWithCRLF = encoded.charAt(0) + "\r\n" + encoded.substring(1);
        byte[] decodedWithSpaces = Base64Util.decode(encodedWithSpaces, Base64Util.DEFAULT);
        byte[] decodedWithTabs = Base64Util.decode(encodedWithTabs, Base64Util.DEFAULT);
        byte[] decodedWithNewlines = Base64Util.decode(encodedWithNewlines, Base64Util.DEFAULT);
        byte[] decodedWithCRLF = Base64Util.decode(encodedWithCRLF, Base64Util.DEFAULT);
        assertEquals(original, new String(decodedWithSpaces, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithTabs, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithNewlines, StandardCharsets.UTF_8));
        assertEquals(original, new String(decodedWithCRLF, StandardCharsets.UTF_8));
    }

    @Test
    public void testPartialDecoding() {
        String oneByteEncoded = "QQ=="; // 'A' in base64
        byte[] oneByteDecoded = Base64Util.decode(oneByteEncoded, Base64Util.DEFAULT);
        assertEquals("A", new String(oneByteDecoded, StandardCharsets.UTF_8));
        String twoByteEncoded = "QUI="; // 'AB' in base64
        byte[] twoByteDecoded = Base64Util.decode(twoByteEncoded, Base64Util.DEFAULT);
        assertEquals("AB", new String(twoByteDecoded, StandardCharsets.UTF_8));
        String threeByteEncoded = "QUJD"; // 'ABC' in base64
        byte[] threeByteDecoded = Base64Util.decode(threeByteEncoded, Base64Util.DEFAULT);
        assertEquals("ABC", new String(threeByteDecoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testBinaryData() {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte)i;
        }
        String encoded = Base64Util.encodeToString(binaryData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(binaryData, decoded);
    }

    @Test
    public void testLargeData() {
        byte[] largeData = new byte[10000];
        new Random(42).nextBytes(largeData);  // Fill with random data
        String encoded = Base64Util.encodeToString(largeData, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(largeData, decoded);
    }

    @Test
    public void testDecodeWithoutPadding() {
        String encodedWithPadding = "QUE=";
        String encodedWithoutPadding = "QUE";
        byte[] decodedWithPadding = Base64Util.decode(encodedWithPadding, Base64Util.DEFAULT);
        byte[] decodedWithoutPadding = Base64Util.decode(encodedWithoutPadding, Base64Util.DEFAULT);
        assertArrayEquals(decodedWithPadding, decodedWithoutPadding);
    }

    @Test
    public void testDecodeByteArray() {
        String original = "Test decode byte array";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(originalBytes, Base64Util.DEFAULT);
        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testDecodeByteArrayWithOffset() {
        String originalStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        byte[] original = originalStr.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Util.encode(original, Base64Util.DEFAULT);
        byte[] paddedEncoded = new byte[encoded.length + 10];
        System.arraycopy(encoded, 0, paddedEncoded, 5, encoded.length);
        byte[] decoded = Base64Util.decode(paddedEncoded, 5, encoded.length, Base64Util.DEFAULT);
        assertEquals(originalStr, new String(decoded, StandardCharsets.UTF_8));
    }
}