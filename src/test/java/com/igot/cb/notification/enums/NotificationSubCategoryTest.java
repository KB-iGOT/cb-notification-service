package com.igot.cb.notification.enums;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class NotificationSubCategoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValuesAndValueOf() {
        NotificationSubCategory[] values = NotificationSubCategory.values();
        assertTrue(values.length > 0);

        for (NotificationSubCategory subCategory : values) {
            assertEquals(subCategory, NotificationSubCategory.valueOf(subCategory.name()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = NotificationSubCategory.class, names = {
            "LIKED_POST", "LIKED_COMMENT", "REPLIED_POST", "REPLIED_COMMENT"
    })
    void testClubbedCategoriesOverrideMethods(NotificationSubCategory subCategory) throws Exception {
        JsonNode data = mapper.readTree("{\"discussionId\":\"123\"}");

        String template = subCategory.messageTemplate();
        assertNotNull(template);
        assertTrue(template.contains("{count}") || template.contains("You have"));

        Duration window = subCategory.clubWindow();
        assertEquals(Duration.ofMinutes(15), window);

        String key = subCategory.clubKey(data);
        assertEquals("123", key);

        assertTrue(subCategory.isShouldClub());
    }

    @ParameterizedTest
    @EnumSource(value = NotificationSubCategory.class, names = {
            "CONTENT_REVIEW_REQUEST",
            "CONTENT_PUBLISHED",
            "CONTENT_SPV_PUBLISHED",
            "CONTENT_REJECTED",
            "CONTENT_EDITED",
            "POST_COMMENT",
            "SEND_CONNECTION_REQUEST",
            "ACCEPTED_CONNECTION_REQUEST",
            "REJECTED_CONNECTION_REQUEST",
            "PROFILE_VERIFICATION",
            "USER_TRANSFER",
            "CONTENT_SHARE",
            "TAGGED_COMMENT",
            "TAGGED_POST",
            "EVENT_PUBLISHED",
            "EVENT_ENROLLED",
            "COURSE_PUBLISHED",
            "PROGRAM_PUBLISHED",
            "LEARN_DISCUSSION_POST_COMMENT",
            "LEARN_DISCUSSION_POST_REPLY",
            "TRANSFER_UPDATE",
            "PROFILE_UPDATE",
            "PROFANITY_CHECK"
    })
    void testNonClubbedCategoriesThrowExceptions(NotificationSubCategory subCategory) throws Exception {
        assertFalse(subCategory.isShouldClub());

        JsonNode dummyData = mapper.readTree("{\"discussionId\":\"x\"}");

        assertThrows(UnsupportedOperationException.class, subCategory::messageTemplate);
        assertThrows(UnsupportedOperationException.class, subCategory::clubWindow);
        assertThrows(UnsupportedOperationException.class, () -> subCategory.clubKey(dummyData));
    }
}
