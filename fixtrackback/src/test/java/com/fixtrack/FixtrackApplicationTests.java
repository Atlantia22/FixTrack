package com.fixtrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // <-- Asegura el mismo entorno web
@ActiveProfiles("test")
class FixtrackApplicationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.defer-datasource-initialization", () -> "true");
		
		// Propiedades esenciales de Fixtrack
		registry.add("fixtrack.encryption-key", () -> "kGBNTm18L6XChRoZlDJvP8dC6aXRgck2hPqGBm8ww58=");
		registry.add("fixtrack.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWZ=");
		registry.add("fixtrack.jwt.expiration", () -> "8h");
		registry.add("fixtrack.cors.allowed-origins", () -> "http://localhost:5173");
		registry.add("fixtrack.portal-url", () -> "http://localhost:5173");
		
		// Credenciales y datos del Administrador inicial para evitar el NULL en full_name
		registry.add("fixtrack.admin.email", () -> "admin@fixtrack.local");
		registry.add("fixtrack.admin.password", () -> "Admin123*");
		registry.add("fixtrack.admin.document-number", () -> "0000000000");
		registry.add("fixtrack.admin.full-name", () -> "Administrador Fixtrack"); // <-- ¡Añade esta línea!
		
		registry.add("fixtrack.seed-data", () -> "false");
		registry.add("fixtrack.demo-users.password", () -> "demo1234*");
	}
	@Test
	void contextLoads() {
	}

}
