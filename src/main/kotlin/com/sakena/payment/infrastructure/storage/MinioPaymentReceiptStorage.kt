package com.sakena.payment.infrastructure.storage

import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.shared.config.ObjectStorageProperties
import com.sakena.shared.domain.DomainException
import com.sakena.user.domain.UserId
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.http.Method
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Stores private payment receipts and exposes them only through short-lived links. */
@Component
class MinioPaymentReceiptStorage(
    private val minioClient: MinioClient,
    private val properties: ObjectStorageProperties,
) : PaymentReceiptStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun store(
        payerId: UserId,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): String {
        val objectKey =
            "payment-receipts/${payerId.value}/${UUID.randomUUID()}${extensionFor(contentType)}"
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(objectKey)
                    .stream(content, sizeBytes, -1)
                    .contentType(contentType)
                    .build(),
            )
        } catch (exception: Exception) {
            throw DomainException("Could not upload the payment receipt: ${exception.message}")
        }
        return objectKey
    }

    override fun presignedUrl(objectKey: String): PaymentReceiptAccess =
        try {
            val url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket)
                    .`object`(objectKey)
                    .expiry(properties.presignedUrlExpirySeconds, TimeUnit.SECONDS)
                    .build(),
            )
            PaymentReceiptAccess(url, properties.presignedUrlExpirySeconds)
        } catch (exception: Exception) {
            throw DomainException("Could not build a payment receipt link: ${exception.message}")
        }

    override fun delete(objectKey: String) {
        runCatching {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(objectKey)
                    .build(),
            )
        }.onFailure {
            log.warn("Could not remove payment receipt '{}': {}", objectKey, it.message)
        }
    }

    private fun extensionFor(contentType: String): String =
        when (contentType.substringBefore(';').trim().lowercase()) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ""
        }
}
