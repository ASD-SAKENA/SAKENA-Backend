package com.sakena

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests. Starts one PostgreSQL container for the test JVM
 * and wires every cached Spring context to it, so one test class cannot stop the
 * database while another class still uses it. Requires Docker to be running.
 */
@SpringBootTest
abstract class IntegrationTest {

    companion object {
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrides(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
