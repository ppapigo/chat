package com.example.chat.presence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomPresenceTrackerTests {
    @Test void multipleTabsCountOneUserAndOnlyLastDisconnectLeaves() {
        var tracker = new RoomPresenceTracker();
        assertTrue(tracker.subscribe("tab1", "sub", 1L, "me@example.com"));
        assertFalse(tracker.subscribe("tab1", "sub", 1L, "me@example.com"));
        assertFalse(tracker.subscribe("tab2", "sub", 1L, "me@example.com"));
        assertEquals(1, tracker.onlineCount(1L));
        assertFalse(tracker.disconnect("tab1").get(0).lastForUser());
        assertEquals(1, tracker.onlineCount(1L));
        assertTrue(tracker.unsubscribe("tab2", "sub").orElseThrow().lastForUser());
        assertEquals(0, tracker.onlineCount(1L));
        assertTrue(tracker.disconnect("tab2").isEmpty());
    }
}
