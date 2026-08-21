package com.sakena.payment.application

import com.sakena.payment.application.command.PaymentReceiptUpload
import com.sakena.shared.domain.DomainValidationException
import org.springframework.stereotype.Component
import java.io.BufferedInputStream
import java.io.InputStream

/** Validates receipt metadata and file signatures before object storage. */
@Component
class PaymentReceiptValidator {

    companion object {
        const val MAX_RECEIPT_BYTES = 5L * 1024 * 1024
        private val ALLOWED_RECEIPT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }

    fun validate(receipt: PaymentReceiptUpload): InputStream {
        if (receipt.sizeBytes <= 0) throw DomainValidationException("Receipt file must not be empty")
        if (receipt.sizeBytes > MAX_RECEIPT_BYTES) {
            throw DomainValidationException("Receipt file must be at most 5 MB")
        }
        val baseType = receipt.contentType.substringBefore(';').trim().lowercase()
        if (baseType !in ALLOWED_RECEIPT_TYPES) {
            throw DomainValidationException("Unsupported receipt type '$baseType'")
        }
        val content = BufferedInputStream(receipt.content)
        content.mark(12)
        val signature = content.readNBytes(12)
        content.reset()
        val signatureMatches = when (baseType) {
            "image/png" -> signature.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            "image/jpeg" -> signature.startsWith(0xFF, 0xD8, 0xFF)
            "image/webp" ->
                signature.startsWith(0x52, 0x49, 0x46, 0x46) &&
                    signature.sliceArray(8 until minOf(12, signature.size))
                        .startsWith(0x57, 0x45, 0x42, 0x50)
            else -> false
        }
        if (!signatureMatches) {
            throw DomainValidationException("Receipt content does not match type '$baseType'")
        }
        return content
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all {
            (this[it].toInt() and 0xFF) == expected[it]
        }
}
