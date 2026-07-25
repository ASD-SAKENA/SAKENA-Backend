package com.sakena.chat.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/** MinIO connection settings for chat attachments (`storage.*` in application.yml). */
@ConfigurationProperties(prefix = "storage")
data class MinioProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "sakena",
    val secretKey: String = "sakena-secret",
    val bucket: String = "sakena-chat",
    /** Lifetime of the presigned download URLs handed to the browser. */
    val presignedUrlExpirySeconds: Int = 900,
)
