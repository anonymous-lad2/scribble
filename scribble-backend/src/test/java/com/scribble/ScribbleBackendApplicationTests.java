package com.scribble;

import com.scribble.testsupport.EmbeddedRedisInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = EmbeddedRedisInitializer.class)
@ActiveProfiles("test")
class ScribbleBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
