package com.example.chat;

import com.example.chat.auth.BoardUserReader;
import com.example.chat.auth.ChatUser;
import com.example.chat.auth.TokenDenylist;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("browser")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:chat-browser;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.ws.allowed-origin-patterns=http://127.0.0.1:*",
        "management.health.redis.enabled=false", "logging.level.com.example.chat=INFO"
})
class BrowserIntegrationTests {
    @LocalServerPort int port;
    @MockitoBean BoardUserReader boardUsers;
    @MockitoBean TokenDenylist denylist;

    @Test void browsersUseActualHttpAndStompServer() throws Exception {
        when(boardUsers.findByUsername("owner@example.com")).thenReturn(Optional.of(new ChatUser(42L, "owner@example.com", "Owner")));
        when(boardUsers.findByUsername("guest@example.com")).thenReturn(Optional.of(new ChatUser(77L, "guest@example.com", "Guest")));
        Path cli = Path.of("frontend/node_modules/playwright/cli.js");
        if (!Files.isRegularFile(cli)) cli = Path.of("node_modules/playwright/cli.js");
        assertTrue(Files.isRegularFile(cli), "Run npm ci before browserIntegrationTest");
        var builder = new ProcessBuilder("node", cli.toAbsolutePath().toString(), "test", "--config=playwright.integration.config.mjs")
                .directory(Path.of("frontend").toFile()).inheritIO();
        builder.environment().put("CHAT_TEST_BASE_URL", "http://127.0.0.1:" + port);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), "Playwright integration test timed out");
            assertEquals(0, process.exitValue(), "Playwright integration test failed; see test-results/integration");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
