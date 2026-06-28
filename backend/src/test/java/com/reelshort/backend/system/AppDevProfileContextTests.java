package com.reelshort.backend.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("app-dev")
class AppDevProfileContextTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoadsWithAppDevProfile() {
		assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
		assertThat(environment.getProperty("reelshort.rate-limit.enabled")).isEqualTo("false");
		assertThat(environment.getProperty("reelshort.content-provider.base-url")).isEqualTo("http://127.0.0.1:5000");
	}
}
