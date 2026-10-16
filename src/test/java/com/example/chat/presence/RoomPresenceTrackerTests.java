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

    @Test void disconnectReleasesAllRoomsOnceWithoutRemovingOtherUsers() {
        var tracker = new RoomPresenceTracker();
        tracker.subscribe("tab1", "first", 1L, "me@example.com");
        tracker.subscribe("tab1", "duplicate-room", 1L, "me@example.com");
        tracker.subscribe("tab1", "second", 2L, "me@example.com");
        tracker.subscribe("tab2", "other-user", 1L, "other@example.com");

        var released = tracker.disconnect("tab1");
        assertEquals(3, released.size());
        assertEquals(2, released.stream().filter(RoomPresenceTracker.Presence::lastForUser).count());
        assertEquals(1, tracker.onlineCount(1L));
        assertEquals(0, tracker.onlineCount(2L));
        assertFalse(tracker.isOnline(1L, "me@example.com"));
        assertTrue(tracker.isOnline(1L, "other@example.com"));

        assertTrue(tracker.disconnect("tab1").isEmpty());
        assertTrue(tracker.unsubscribe("tab1", "first").isEmpty());
        assertEquals(1, tracker.onlineCount(1L));
    }
}
