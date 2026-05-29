package com.example.campusmate.domain.llm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

internal object LlmHttpUtils {
    fun timeoutMillis(value: Int): Int = value.coerceIn(1_000, 120_000)

    fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    fun failureForHttpCode(code: Int, body: String?, apiKey: String): LlmGenerateResult.Failure {
        val message = when (code) {
            401, 403 -> "\u8ba4\u8bc1\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5 API Key\u3001Base URL \u548c\u670d\u52a1\u5546\u6743\u9650"
            429 -> "\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u6216\u68c0\u67e5\u670d\u52a1\u5546\u989d\u5ea6"
            in 500..599 -> "\u6a21\u578b\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
            else -> "\u6a21\u578b\u8bf7\u6c42\u5931\u8d25\uff08HTTP $code\uff09"
        }
        return LlmGenerateResult.Failure(
            message = message,
            detail = sanitizeDetail(body, apiKey),
            recoverable = code != 401 && code != 403
        )
    }

    fun failureForException(error: Exception): LlmGenerateResult.Failure {
        return when (error) {
            is SocketTimeoutException -> LlmGenerateResult.Failure(
                message = "\u8fde\u63a5\u8d85\u65f6\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u3001Base URL \u6216\u8d85\u65f6\u65f6\u95f4",
                detail = error.message
            )
            is IOException -> LlmGenerateResult.Failure(
                message = "\u7f51\u7edc\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u548c\u670d\u52a1\u5546\u914d\u7f6e",
                detail = error.message
            )
            else -> LlmGenerateResult.Failure(
                message = "\u6a21\u578b\u8bf7\u6c42\u5904\u7406\u5931\u8d25",
                detail = error.message
            )
        }
    }

    private fun sanitizeDetail(raw: String?, apiKey: String): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed
            .replace(apiKey, "[api-key]")
            .take(MAX_DETAIL_LENGTH)
    }

    private const val MAX_DETAIL_LENGTH = 500
}
