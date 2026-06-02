package com.shade.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorTest {

    // ── toAppError ────────────────────────────────────────────────────────────

    @Test
    fun `UnknownHostException maps to NoInternetError`() {
        val error = UnknownHostException("host not found").toAppError()
        assertThat(error).isInstanceOf(AppError.NoInternetError::class.java)
    }

    @Test
    fun `ConnectException maps to NoInternetError`() {
        val error = ConnectException("connection refused").toAppError()
        assertThat(error).isInstanceOf(AppError.NoInternetError::class.java)
    }

    @Test
    fun `SocketTimeoutException maps to TimeoutError`() {
        val error = SocketTimeoutException("timed out").toAppError()
        assertThat(error).isInstanceOf(AppError.TimeoutError::class.java)
    }

    @Test
    fun `AppError subclass round-trips through toAppError`() {
        val original: AppError = AppError.UnauthorizedError
        val result = (original as Throwable).toAppError()
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `generic RuntimeException maps to UnknownError`() {
        val cause = RuntimeException("something broke")
        val error = cause.toAppError()
        assertThat(error).isInstanceOf(AppError.UnknownError::class.java)
        val unknown = error as AppError.UnknownError
        assertThat(unknown.throwable).isEqualTo(cause)
    }

    // ── toUserMessage ─────────────────────────────────────────────────────────

    @Test
    fun `NoInternetError has readable user message`() {
        val msg = AppError.NoInternetError.toUserMessage()
        assertThat(msg).isNotEmpty()
        assertThat(msg).doesNotContain("Exception")
    }

    @Test
    fun `HttpError 500 shows generic server error message`() {
        val msg = AppError.HttpError(500).toUserMessage()
        assertThat(msg).contains("Sunucu")
    }

    @Test
    fun `HttpError 401 shows code in message`() {
        val msg = AppError.HttpError(401).toUserMessage()
        assertThat(msg).contains("401")
    }

    @Test
    fun `UnauthorizedError toUserMessage does not expose technical details`() {
        val msg = AppError.UnauthorizedError.toUserMessage()
        assertThat(msg).doesNotContain("JWT")
        assertThat(msg).doesNotContain("token")
        assertThat(msg).isNotEmpty()
    }

    // ── AppError properties ───────────────────────────────────────────────────

    @Test
    fun `HttpError carries code and optional body`() {
        val error = AppError.HttpError(404, "Not found")
        assertThat(error.code).isEqualTo(404)
        assertThat(error.body).isEqualTo("Not found")
        assertThat(error.message).contains("404")
    }

    @Test
    fun `CryptoError carries detail string`() {
        val error = AppError.CryptoError("bad padding")
        assertThat(error.detail).isEqualTo("bad padding")
        assertThat(error.message).contains("bad padding")
    }

    @Test
    fun `DatabaseError carries detail string`() {
        val error = AppError.DatabaseError("unique constraint")
        assertThat(error.detail).isEqualTo("unique constraint")
    }
}
