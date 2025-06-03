package com.igot.cb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PayloadValidationTest {

    @InjectMocks
    private PayloadValidation payloadValidation;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testValidatePayload_InvalidJsonNode_ThrowsCustomException() throws Exception {
        // Invalid payload: missing "exampleField"
        String jsonString = "{\"invalidField\": \"value\"}";
        JsonNode payload = objectMapper.readTree(jsonString);

        String schemaFilePath = "/schema/comment-tree-request.json"; // starts with '/' for getResourceAsStream

        CustomException exception = assertThrows(CustomException.class, () ->
                payloadValidation.validatePayload(schemaFilePath, payload)
        );

        assertEquals("Failed to validate payload", exception.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
    }

    @Test
    void testValidatePayload_ValidJsonNode_DoesNotThrowException() throws Exception {
        // JSON payload that satisfies the schema (has "exampleField")
        String validJson = "{\"exampleField\": \"valid value\"}";
        JsonNode payload = objectMapper.readTree(validJson);

        // Path to schema that defines "exampleField" as required
        String schemaFilePath = "/schema/comment-tree-valid-request.json";

        // Assert that no exception is thrown
        assertDoesNotThrow(() ->
                payloadValidation.validatePayload(schemaFilePath, payload)
        );
    }

    @Test
    void testValidatePayload_WithJsonArray_DoesNotThrowException() throws Exception {
        // JSON array payload: each element satisfies schema with "exampleField"
        String jsonArray = "[{\"exampleField\":\"value1\"}, {\"exampleField\":\"value2\"}]";

        JsonNode payload = objectMapper.readTree(jsonArray);
        String schemaFilePath = "/schema/comment-tree-valid-request.json";

        assertDoesNotThrow(() -> payloadValidation.validatePayload(schemaFilePath, payload));
    }
}
