package com.igot.cb.notification.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class NotificationReadStatusTest {

    @Test
    void testValuesContainsAllConstants() {
        NotificationReadStatus[] statuses = NotificationReadStatus.values();
        assertEquals(3, statuses.length);
        assertArrayEquals(
                new NotificationReadStatus[]{
                        NotificationReadStatus.READ,
                        NotificationReadStatus.UNREAD,
                        NotificationReadStatus.BOTH
                },
                statuses
        );
    }

    @ParameterizedTest
    @EnumSource(NotificationReadStatus.class)
    void testValueOfWorksForEachConstant(NotificationReadStatus status) {
        String name = status.name();
        assertEquals(status, NotificationReadStatus.valueOf(name));
    }

    @Test
    void testValueOfInvalidNameThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> NotificationReadStatus.valueOf("INVALID"));
    }

    @ParameterizedTest
    @EnumSource(NotificationReadStatus.class)
    void testToStringReturnsName(NotificationReadStatus status) {
        assertEquals(status.name(), status.toString());
    }
}
