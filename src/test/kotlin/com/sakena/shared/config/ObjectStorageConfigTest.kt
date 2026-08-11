package com.sakena.shared.config

import io.minio.GetPresignedObjectUrlArgs
import io.minio.http.Method
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class ObjectStorageConfigTest {

    @Test
    fun `signed URLs use the browser-accessible endpoint`() {
        val properties =
            ObjectStorageProperties(
                endpoint = "http://minio:9000",
                publicEndpoint = "https://files.example.test",
                accessKey = "access-key",
                secretKey = "secret-key",
                bucket = "receipts",
                region = "us-east-1",
            )
        val publicClient = ObjectStorageConfig().publicMinioClient(properties)

        val signedUrl =
            publicClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket)
                    .`object`("payment-receipts/resident/receipt.png")
                    .expiry(properties.presignedUrlExpirySeconds)
                    .build(),
            )

        val uri = URI(signedUrl)
        assertThat(uri.host).isEqualTo("files.example.test")
        assertThat(uri.scheme).isEqualTo("https")
        assertThat(uri.rawQuery).contains("X-Amz-Signature=")
    }
}
