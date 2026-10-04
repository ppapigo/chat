package com.example.chat.presence;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RoomPresenceTracker {
    public record Presence(Long roomId, String email, boolean lastForUser){}

    public record Subscription(Long roomId, String email){}

    private final Map<String, Map<String, Subscription>> bySession = new HashMap<>();
    private final Map<Long, Map<String, Integer>> byRoom = new HashMap<>();

    public synchronized boolean subscribe(String sessionId, String subscriptionId, Long roomId, String email){
        bySession.computeIfAbsent(
          sessionId,k-> new HashMap<>())
                        .put(subscriptionId, new Subscription(roomId, email));

          Map<String, Integer> users = byRoom.computeIfAbsent(roomId,k->new HashMap<>());
          int count = users.merge(email,1,Integer::sum);
          return count==1;

    }
    public synchronized Optional<Presence> unsubscribe(String sessionId, String subscriptionId){
        Map<String, Subscription>subs=bySession.get(sessionId);
        if (subs == null){
            return Optional.empty();
        }

        Subscription sub = subs.remove(subscriptionId);
        if(subs.isEmpty()){
            bySession.remove(sessionId);
        }

        return sub == null ? Optional.empty():Optional.of(release(sub));
    }

    private Presence release(Subscription sub){
        Map<String, Integer> users = byRoom.get(sub.roomId);
        boolean last = false;
        if (users != null){
            Integer remaining = users.merge(sub.email(), -1, Integer::sum);
            if (remaining == null || remaining <=0){
                users.remove(sub.email());
                last=true;
            }

            if (users.isEmpty()){
                byRoom.remove(sub.roomId);
            }
        }
        return new Presence(sub.roomId(), sub.email(), last);
    }

    public synchronized List<Presence> disconnect(String sessionId){
        Map<String, Subscription> subs=bySession.remove(sessionId);
        List<Presence> released = new ArrayList<>();
        if(subs != null){
            subs.values().forEach(sub->released.add(release(sub)));
        }
        return released;
    }

    public synchronized boolean isOnline(Long roomId, String email){
        Map<String, Integer> users = byRoom.get(roomId);
        return users != null && users.containsKey(email);
    }

    public synchronized int onlineCount(Long roomId){
        Map<String, Integer> users = byRoom.get(roomId);
        return users == null ? 0: users.size();
    }
}
