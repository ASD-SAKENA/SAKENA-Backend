package com.sakena.payment.domain

import com.sakena.user.domain.UserId
import java.io.InputStream

data class PaymentReceiptAccess(
    val url: String,
    val expiresInSeconds: Int,
)

/** Object-storage port for private payment receipt images. */
interface PaymentReceiptStorage {
    fun store(
        payerId: UserId,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): String

    fun presignedUrl(objectKey: String): PaymentReceiptAccess

    fun delete(objectKey: String)
}
