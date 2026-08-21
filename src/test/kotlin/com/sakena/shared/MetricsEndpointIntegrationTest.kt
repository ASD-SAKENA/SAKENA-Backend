package com.sakena.shared

import com.sakena.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertTrue

/**
 * Prometheus scrapes /actuator/prometheus in-cluster, so the endpoint has to
 * exist and actually carry metrics — without the micrometer registry it 404s
 * and the dashboards would simply stay empty.
 *
 * Actuator runs on its own port in production (never routed by the public
 * ingress); the test drives that same port so the wiring it asserts is the
 * wiring that ships.
 */
@AutoConfigureObservability
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
class MetricsEndpointIntegrationTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Value("\${local.management.port}") private val managementPort: Int,
) : IntegrationTest() {

    @Test
    fun `the prometheus endpoint serves JVM metrics tagged with the application`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$managementPort/actuator/prometheus",
            String::class.java,
        )
        val body = response.body ?: ""

        assertTrue(response.statusCode.is2xxSuccessful, "scrape failed: ${response.statusCode}")
        assertTrue(body.contains("jvm_memory_used_bytes"), "JVM metrics missing")
        assertTrue(
            body.contains("application=\"sakena-backend\""),
            "the application tag every dashboard filters on is missing",
        )
    }

    @Test
    fun `health is served on the management port for the kubelet probes`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$managementPort/actuator/health",
            String::class.java,
        )

        assertTrue(response.statusCode.is2xxSuccessful, "probe endpoint failed: ${response.statusCode}")
    }
}
