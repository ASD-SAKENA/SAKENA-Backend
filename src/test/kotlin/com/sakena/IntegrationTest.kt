package com.sakena

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for integration tests. Spins up a real PostgreSQL container once and
 * wires it into Spring via [ServiceConnection], so tests run against the same
 * database engine (and Flyway migrations) as production. Requires Docker to be running.
 */
@SpringBootTest
@Testcontainers
abstract class IntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun overrides(registry: DynamicPropertyRegistry) {
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
