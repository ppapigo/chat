package com.example.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Requires a running MySQL service and external application credentials.
@EnabledIfEnvironmentVariable(named = "RUN_CHAT_INTEGRATION_TESTS", matches = "true")
@SpringBootTest
class ChatApplicationTests {

	@Test
	void contextLoads() {
	}

}
