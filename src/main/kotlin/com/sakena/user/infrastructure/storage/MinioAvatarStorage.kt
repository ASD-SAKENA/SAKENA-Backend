package com.sakena.user.infrastructure.storage

import com.sakena.user.domain.AvatarStorage
import com.sakena.user.domain.UserId

import com.sakena.shared.config.ObjectStorageProperties
import com.sakena.shared.domain.DomainException
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.http.Method
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Adapter implementing the [AvatarStorage] port on top of MinIO.
 * Objects are stored privately under `chat/{buildingId}/{uuid}{ext}` and served
 * through short-lived presigned URLs, so attachments are never public.
 */
@Component
class MinioAvatarStorage(
    private val minioClient: MinioClient,
    @Qualifier("publicMinioClient")
    private val publicMinioClient: MinioClient,
    private val properties: ObjectStorageProperties,
) : AvatarStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creating the bucket is best-effort: a missing MinIO must not stop the
     * application from booting, since the rest of the product works without it.
     */
    @PostConstruct
    fun ensureBucket() {
        runCatching {
            val exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket).build(),
            )
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
            }
        }.onFailure {
            log.warn("Could not verify MinIO bucket '{}': {}", properties.bucket, it.message)
        }
    }

    override fun store(
        userId: UserId,
        originalFilename: String?,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): String {
        val key = "avatars/${userId.value}/${UUID.randomUUID()}${extensionOf(originalFilename)}"
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(key)
                    .stream(content, sizeBytes, -1)
                    .contentType(contentType)
                    .build(),
            )
        } catch (e: Exception) {
            throw DomainException("Could not upload the attachment: ${e.message}")
        }
        return key
    }

    override fun presignedUrl(storageKey: String): String =
        try {
            publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket)
                    .`object`(storageKey)
                    .expiry(properties.presignedUrlExpirySeconds, TimeUnit.SECONDS)
                    .build(),
            )
        } catch (e: Exception) {
            throw DomainException("Could not build a download link for the attachment: ${e.message}")
        }

    override fun delete(storageKey: String) {
        // A failed cleanup must not roll back the message deletion the user asked for.
        runCatching {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(storageKey)
                    .build(),
            )
        }.onFailure {
            log.warn("Could not remove avatar '{}': {}", storageKey, it.message)
        }
    }

    private fun extensionOf(filename: String?): String {
        val dot = filename?.lastIndexOf('.') ?: -1
        if (filename == null || dot <= 0 || dot == filename.lastIndex) return ""
        val extension = filename.substring(dot)
        // Guard against path traversal or absurd extensions sneaking into the key.
        return if (extension.length <= 10 && extension.matches(Regex("^\\.[A-Za-z0-9]+$"))) {
            extension.lowercase()
        } else {
            ""
        }
    }
}
