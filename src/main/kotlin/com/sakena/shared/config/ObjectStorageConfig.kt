package com.sakena.shared.config

import io.minio.MinioClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Shared MinIO settings for private object storage (`storage.*` in application.yml). */
@ConfigurationProperties(prefix = "storage")
data class ObjectStorageProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "sakena",
    val secretKey: String = "sakena-secret",
    val bucket: String = "sakena-chat",
    /** Lifetime of presigned download URLs handed to clients. */
    val presignedUrlExpirySeconds: Int = 900,
)

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties::class)
class ObjectStorageConfig {

    @Bean
    fun minioClient(properties: ObjectStorageProperties): MinioClient =
        MinioClient.builder()
            .endpoint(properties.endpoint)
            .credentials(properties.accessKey, properties.secretKey)
            .build()
}
