package com.fixtrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles; // <-- Importante

@SpringBootTest
@ActiveProfiles("test") // <-- Esto fuerza a usar el application.yml de pruebas
class FixtrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
