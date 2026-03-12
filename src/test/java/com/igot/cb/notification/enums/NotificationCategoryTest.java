package com.igot.cb.notification.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class NotificationCategoryTest {

    @Test
    void testValuesContainsAllConstants() {
        NotificationCategory[] categories = NotificationCategory.values();
        assertEquals(7, categories.length);
        assertArrayEquals(
                new NotificationCategory[]{
                        NotificationCategory.LEARN,
                        NotificationCategory.DISCUSSION,
                        NotificationCategory.EVENT,
                        NotificationCategory.NETWORK,
                        NotificationCategory.PROFILE,
                        NotificationCategory.CONTENT,
                        NotificationCategory.PEER_VALIDATION
                },
                categories
        );
    }

    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void testValueOfWorksForEachConstant(NotificationCategory category) {
        String name = category.name();
        assertEquals(category, NotificationCategory.valueOf(name));
    }

    @Test
    void testValueOfInvalidNameThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> NotificationCategory.valueOf("INVALID_NAME"));
    }

    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void testToStringReturnsName(NotificationCategory category) {
        assertEquals(category.name(), category.toString());
    }
}
