package com.sakena.user.application

import java.io.InputStream

/** Bytes handed to the storage port, kept free of Spring's MultipartFile. */
data class AvatarUpload(
    val originalFilename: String?,
    val contentType: String,
    val sizeBytes: Long,
    val content: InputStream,
)
