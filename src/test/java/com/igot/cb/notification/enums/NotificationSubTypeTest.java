package com.igot.cb.notification.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationSubTypeTest {

    @Test
    void testValues() {
        NotificationSubType[] values = NotificationSubType.values();
        assertEquals(4, values.length);
        assertArrayEquals(
                new NotificationSubType[]{
                        NotificationSubType.ALERT,
                        NotificationSubType.UPDATE,
                        NotificationSubType.ENGAGEMENT,
                        NotificationSubType.PROMOTIONAL
                },
                values
        );
    }

    @Test
    void testValueOf() {
        assertEquals(NotificationSubType.ALERT, NotificationSubType.valueOf("ALERT"));
        assertEquals(NotificationSubType.UPDATE, NotificationSubType.valueOf("UPDATE"));
        assertEquals(NotificationSubType.ENGAGEMENT, NotificationSubType.valueOf("ENGAGEMENT"));
        assertEquals(NotificationSubType.PROMOTIONAL, NotificationSubType.valueOf("PROMOTIONAL"));
    }
}
