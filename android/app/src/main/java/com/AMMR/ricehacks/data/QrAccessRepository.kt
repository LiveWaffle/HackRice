package com.AMMR.ricehacks.data

import android.util.Base64
import kotlinx.coroutines.delay
import java.security.SecureRandom
import java.util.UUID

data class SignedQrToken(
    val tokenId: String,
    val signedToken: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
)

data class DoctorScanRequest(
    val requestId: String,
    val doctorName: String,
    val hospitalName: String,
    val deviceInfo: String,
    val tokenId: String,
    val requestedAtMillis: Long
)

data class AuditLogEntry(
    val timestampMillis: Long,
    val doctorDeviceId: String,
    val hospitalId: String,
    val tokenId: String,
    val approvalResult: ApprovalResult
)

enum class ApprovalResult {
    Pending,
    Approved,
    Denied,
    TimedOut,
    Invalid,
    Expired,
    Reused,
    UnauthenticatedDevice
}

data class TokenValidationResult(
    val accepted: Boolean,
    val result: ApprovalResult,
    val request: DoctorScanRequest? = null
)

interface QrAccessRepository {
    suspend fun requestSignedAccessToken(): SignedQrToken

    suspend fun validateScannedToken(
        signedToken: String,
        doctorDeviceId: String,
        hospitalId: String
    ): TokenValidationResult

    suspend fun getPendingApprovalRequest(tokenId: String): DoctorScanRequest?

    suspend fun submitApprovalDecision(
        requestId: String,
        approved: Boolean
    ): ApprovalResult

    suspend fun getAuditLog(): List<AuditLogEntry>
}

class FakeQrAccessRepository : QrAccessRepository {
    private val secureRandom = SecureRandom()
    private var activeToken: SignedQrToken? = null
    private var activeRequest: DoctorScanRequest? = null
    private val usedTokenIds = mutableSetOf<String>()
    private val validationAttemptsByToken = mutableMapOf<String, Int>()
    private val auditLogs = mutableListOf<AuditLogEntry>()
    private var firstApprovalPollAtMillis: Long? = null

    override suspend fun requestSignedAccessToken(): SignedQrToken {
        delay(250)

        val now = System.currentTimeMillis()
        val nonce = ByteArray(24).also(secureRandom::nextBytes)
        val tokenId = UUID.randomUUID().toString()

        activeRequest = null
        firstApprovalPollAtMillis = null
        activeToken = SignedQrToken(
            tokenId = tokenId,
            signedToken = buildServerSignedOpaqueToken(tokenId, nonce),
            issuedAtMillis = now,
            expiresAtMillis = now + TOKEN_TTL_MILLIS
        )

        return activeToken!!
    }

    override suspend fun validateScannedToken(
        signedToken: String,
        doctorDeviceId: String,
        hospitalId: String
    ): TokenValidationResult {
        delay(250)

        val token = activeToken
        val now = System.currentTimeMillis()

        if (!isRecognizedDoctorDevice(doctorDeviceId, hospitalId)) {
            logAttempt(doctorDeviceId, hospitalId, token?.tokenId.orEmpty(), ApprovalResult.UnauthenticatedDevice)
            return TokenValidationResult(accepted = false, result = ApprovalResult.UnauthenticatedDevice)
        }

        if (token == null || signedToken != token.signedToken) {
            logAttempt(doctorDeviceId, hospitalId, token?.tokenId.orEmpty(), ApprovalResult.Invalid)
            return TokenValidationResult(accepted = false, result = ApprovalResult.Invalid)
        }

        val attempts = validationAttemptsByToken.getOrDefault(token.tokenId, 0) + 1
        validationAttemptsByToken[token.tokenId] = attempts
        if (attempts > MAX_VALIDATION_ATTEMPTS) {
            logAttempt(doctorDeviceId, hospitalId, token.tokenId, ApprovalResult.Invalid)
            return TokenValidationResult(accepted = false, result = ApprovalResult.Invalid)
        }

        if (now > token.expiresAtMillis) {
            logAttempt(doctorDeviceId, hospitalId, token.tokenId, ApprovalResult.Expired)
            return TokenValidationResult(accepted = false, result = ApprovalResult.Expired)
        }

        if (usedTokenIds.contains(token.tokenId)) {
            logAttempt(doctorDeviceId, hospitalId, token.tokenId, ApprovalResult.Reused)
            return TokenValidationResult(accepted = false, result = ApprovalResult.Reused)
        }

        usedTokenIds.add(token.tokenId)
        val request = DoctorScanRequest(
            requestId = UUID.randomUUID().toString(),
            doctorName = "Dr. Maya Chen",
            hospitalName = "Rice Medical Center",
            deviceInfo = "Hospital tablet ending in 4821",
            tokenId = token.tokenId,
            requestedAtMillis = now
        )
        activeRequest = request
        logAttempt(doctorDeviceId, hospitalId, token.tokenId, ApprovalResult.Pending)

        return TokenValidationResult(
            accepted = true,
            result = ApprovalResult.Pending,
            request = request
        )
    }

    override suspend fun getPendingApprovalRequest(tokenId: String): DoctorScanRequest? {
        delay(150)

        val token = activeToken ?: return null
        if (token.tokenId != tokenId || System.currentTimeMillis() > token.expiresAtMillis) {
            return null
        }

        val firstPoll = firstApprovalPollAtMillis ?: System.currentTimeMillis().also {
            firstApprovalPollAtMillis = it
        }

        // Simulates the doctor app scanning this QR through the backend.
        if (activeRequest == null && System.currentTimeMillis() - firstPoll > DEMO_SCAN_DELAY_MILLIS) {
            validateScannedToken(
                signedToken = token.signedToken,
                doctorDeviceId = "hospital-device-4821",
                hospitalId = "rice-medical-center"
            )
        }

        return activeRequest
    }

    override suspend fun submitApprovalDecision(
        requestId: String,
        approved: Boolean
    ): ApprovalResult {
        delay(200)

        val request = activeRequest ?: return ApprovalResult.Invalid
        if (request.requestId != requestId) return ApprovalResult.Invalid

        val result = if (approved) ApprovalResult.Approved else ApprovalResult.Denied
        updateLatestPendingLog(request.tokenId, result)
        activeRequest = null

        return result
    }

    override suspend fun getAuditLog(): List<AuditLogEntry> {
        delay(100)
        return auditLogs.toList().asReversed()
    }

    private fun buildServerSignedOpaqueToken(tokenId: String, nonce: ByteArray): String {
        val nonceText = Base64.encodeToString(nonce, Base64.NO_WRAP or Base64.URL_SAFE)
        val signaturePlaceholder = Base64.encodeToString(
            ByteArray(32).also(secureRandom::nextBytes),
            Base64.NO_WRAP or Base64.URL_SAFE
        )

        // A real backend signs patient-id, iat, exp, and nonce with its private key.
        // The client receives only this opaque signed token, never the raw patient ID.
        return "hbqr.$tokenId.$nonceText.$signaturePlaceholder"
    }

    private fun isRecognizedDoctorDevice(
        doctorDeviceId: String,
        hospitalId: String
    ): Boolean = doctorDeviceId.startsWith("hospital-device-") && hospitalId.isNotBlank()

    private fun logAttempt(
        doctorDeviceId: String,
        hospitalId: String,
        tokenId: String,
        result: ApprovalResult
    ) {
        auditLogs.add(
            AuditLogEntry(
                timestampMillis = System.currentTimeMillis(),
                doctorDeviceId = doctorDeviceId,
                hospitalId = hospitalId,
                tokenId = tokenId,
                approvalResult = result
            )
        )
    }

    private fun updateLatestPendingLog(
        tokenId: String,
        result: ApprovalResult
    ) {
        val index = auditLogs.indexOfLast {
            it.tokenId == tokenId && it.approvalResult == ApprovalResult.Pending
        }

        if (index >= 0) {
            auditLogs[index] = auditLogs[index].copy(approvalResult = result)
        }
    }

    companion object {
        const val TOKEN_TTL_SECONDS = 45
        const val REFRESH_SECONDS = 30
        const val APPROVAL_TIMEOUT_SECONDS = 20

        private const val TOKEN_TTL_MILLIS = TOKEN_TTL_SECONDS * 1000L
        private const val DEMO_SCAN_DELAY_MILLIS = 8_000L
        private const val MAX_VALIDATION_ATTEMPTS = 5
    }
}
