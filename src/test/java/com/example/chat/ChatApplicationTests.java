package com.example.chat;

import com.example.chat.auth.BoardUserReader;
import com.example.chat.auth.ChatUser;
import com.example.chat.auth.TokenDenylist;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:chat-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.ws.allowed-origin-patterns=http://localhost:*", "logging.level.com.example.chat=INFO",
        "management.health.redis.enabled=false"
})
class ChatApplicationTests {
    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @MockitoBean BoardUserReader boardUsers;
    @MockitoBean TokenDenylist denylist;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void users() {
        when(boardUsers.findByUsername("owner@example.com")).thenReturn(Optional.of(new ChatUser(42L, "owner@example.com", "Owner")));
        when(boardUsers.findByUsername("guest@example.com")).thenReturn(Optional.of(new ChatUser(77L, "guest@example.com", "Guest")));
    }

    private String token(String email, String jti, Instant expiry) {
        return Jwts.builder().subject(email).id(jti).expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
    }
    private String token(String email) { return token(email, UUID.randomUUID().toString(), Instant.now().plusSeconds(600)); }
    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private long createRoom(String token) throws Exception {
        var response = request("POST", "/api/chat/rooms", token, "{\"name\":\"room-" + UUID.randomUUID() + "\",\"description\":\"integration\"}");
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).path("id").asLong();
    }

    @Test
    void frontendIsPublicWhileApiRequiresAuth() throws Exception {
        assertEquals(200, request("GET", "/", null, null).statusCode());
        assertEquals(200, request("GET", "/assets/styles.css", null, null).statusCode());
        assertEquals(200, request("GET", "/assets/js/app.js", null, null).statusCode());
        assertEquals(200, request("GET", "/assets/vendor/stomp.umd.min.js", null, null).statusCode());
        assertEquals(200, request("GET", "/actuator/health", null, null).statusCode());
        assertEquals(401, request("GET", "/api/chat/rooms", null, null).statusCode());
        assertEquals("owner@example.com", request("GET", "/api/chat/me", token("owner@example.com"), null).body());
    }

    @Test
    void membershipIsPersistedAndOnlyOwnerCanDelete() throws Exception {
        String owner = token("owner@example.com"), guest = token("guest@example.com");
        long id = createRoom(owner); String base = "/api/chat/rooms/" + id;
        assertEquals(404, request("GET", base + "/messages", guest, null).statusCode());
        assertEquals(200, request("POST", base + "/join", guest, null).statusCode());
        // A second join is idempotent and must not create an extra membership.
        assertEquals(200, request("POST", base + "/join", guest, null).statusCode());
        var members = json.readTree(request("GET", base + "/members", guest, null).body());
        assertEquals(2, members.size()); assertEquals(77, members.get(1).path("userId").asLong());
        assertEquals(200, request("GET", base + "/messages", guest, null).statusCode());
        assertEquals(403, request("DELETE", base, guest, null).statusCode());
        assertEquals(204, request("DELETE", base + "/leave", guest, null).statusCode());
        assertEquals(404, request("GET", base + "/members", guest, null).statusCode());
        assertEquals(204, request("DELETE", base, owner, null).statusCode());
        assertEquals(404, request("GET", base, owner, null).statusCode());
    }

    @Test
    void validationAndRevokedTokensProduceStructuredErrors() throws Exception {
        String owner = token("owner@example.com");
        assertEquals(400, request("POST", "/api/chat/rooms", owner, "{\"name\":\"   \"}").statusCode());
        assertEquals(400, request("POST", "/api/chat/rooms", owner, "{broken}").statusCode());
        when(denylist.isDenied("revoked")).thenReturn(true);
        assertEquals(401, request("GET", "/api/chat/me", token("owner@example.com", "revoked", Instant.now().plusSeconds(600)), null).statusCode());
        assertEquals(401, request("GET", "/api/chat/me", "malformed", null).statusCode());
    }

    @Test
    void realStompMessagePersistsAndPresenceIsReleased() throws Exception {
        String owner = token("owner@example.com"); long id = createRoom(owner);
        try (Socket socket = new Socket(owner)) {
            assertTrue(socket.frame("CONNECTED").startsWith("CONNECTED"));
            socket.send("SUBSCRIBE\nid:room\ndestination:/topic/room/" + id + "\n\n\0");
            eventually(() -> onlineCount(id, owner) == 1);
            socket.send("SEND\ndestination:/app/room/" + id + "/message\ncontent-type:application/json\n\n{\"content\":\"안녕하세요\"}\0");
            String received;
            do { received = socket.frame("MESSAGE"); } while (!received.contains("안녕하세요"));
            var body = json.readTree(received.substring(received.indexOf("\n\n") + 2).replace("\0", ""));
            assertEquals("TALK", body.path("type").asText());
            var history = json.readTree(request("GET", "/api/chat/rooms/" + id + "/messages?size=1", owner, null).body());
            assertEquals("안녕하세요", history.path("messages").get(0).path("content").asText());
            assertTrue(history.path("hasMore").asBoolean());
            long cursor = history.path("nextBefore").asLong();
            var earlier = json.readTree(request("GET", "/api/chat/rooms/" + id + "/messages?before=" + cursor + "&size=1", owner, null).body());
            assertTrue(earlier.path("messages").get(0).path("id").asLong() < cursor);
            socket.send("UNSUBSCRIBE\nid:room\n\n\0");
            eventually(() -> onlineCount(id, owner) == 0);
            socket.send("SUBSCRIBE\nid:again\ndestination:/topic/room/" + id + "\n\n\0");
            eventually(() -> onlineCount(id, owner) == 1);
        }
        eventually(() -> onlineCount(id, owner) == 0);
    }

    @Test
    void stompRejectsInvalidTokensAndNonMemberSubscriptions() throws Exception {
        String owner = token("owner@example.com"); long id = createRoom(owner);
        try (Socket socket = new Socket("invalid")) { assertTrue(socket.frame("ERROR").contains("code:LOGIN_REQUIRED")); }
        try (Socket socket = new Socket(token("guest@example.com"))) {
            socket.frame("CONNECTED"); socket.send("SUBSCRIBE\nid:forbidden\ndestination:/topic/room/" + id + "\n\n\0");
            assertTrue(socket.frame("ERROR").contains("code:NOT_ROOM_MEMBER"));
        }
        try (Socket socket = new Socket(owner)) {
            socket.frame("CONNECTED");
            socket.send("SEND\ndestination:/topic/room/" + id + "\n\nforged\0");
            assertTrue(socket.frame("ERROR").contains("code:ACCESS_DENIED"));
        }
    }

    private int onlineCount(long id, String token) {
        try { return json.readTree(request("GET", "/api/chat/rooms/" + id, token, null).body()).path("onlineCount").asInt(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(50); }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous presence update");
    }
    private class Socket implements WebSocket.Listener, AutoCloseable {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private final WebSocket socket;
        Socket(String token) throws Exception {
            socket = http.newWebSocketBuilder().subprotocols("v12.stomp").buildAsync(URI.create("ws://localhost:" + port + "/ws"), this).get(10, TimeUnit.SECONDS);
            send("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer " + token + "\n\n\0");
        }
        void send(String frame) { socket.sendText(frame, true).join(); }
        String frame(String command) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                String value = frames.poll(1, TimeUnit.SECONDS);
                if (value != null && value.startsWith(command)) return value;
                if (value != null && value.startsWith("ERROR")) fail("Unexpected STOMP error: " + value);
            }
            throw new AssertionError("No STOMP " + command + " frame");
        }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) { String value = buffer.toString().stripLeading(); buffer.setLength(0); if (!value.isBlank()) frames.add(value); }
            socket.request(1); return null;
        }
        @Override public void close() { socket.abort(); }
    }
}
