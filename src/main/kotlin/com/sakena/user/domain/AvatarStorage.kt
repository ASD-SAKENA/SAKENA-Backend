package com.sakena.user.domain

import java.io.InputStream

/**
 * Outbound port for the object storage holding profile pictures. Keeps MinIO
 * out of the domain and application layers.
 */
interface AvatarStorage {
    /** Stores the bytes and returns the storage key they were written under. */
    fun store(
        userId: UserId,
        originalFilename: String?,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): String

    /** A short-lived URL the browser can use to fetch the image directly. */
    fun presignedUrl(storageKey: String): String

    fun delete(storageKey: String)
}
