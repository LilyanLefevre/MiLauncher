package com.lilyan_lefevre.milauncher.remote

import java.security.MessageDigest

/** Checks the pairing code and locks every attempt out for a while after too many wrong ones. */
internal class PinGuard(
    private val maxFailures: Int = 5,
    private val lockMillis: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var failures = 0
    private var lockedUntil = 0L

    enum class Result { ACCEPTED, REJECTED, LOCKED }

    @Synchronized
    fun check(given: String?, expected: String): Result {
        if (now() < lockedUntil) return Result.LOCKED
        // Constant-time comparison: the response time does not reveal matching digits.
        if (given != null && MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())) {
            failures = 0
            return Result.ACCEPTED
        }
        if (++failures >= maxFailures) {
            failures = 0
            lockedUntil = now() + lockMillis
        }
        return Result.REJECTED
    }
}
