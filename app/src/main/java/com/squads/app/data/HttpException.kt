package com.squads.app.data

import org.json.JSONException
import org.json.JSONObject

/**
 * A non-2xx HTTP response with a bounded, single-line message.
 *
 * Raw response bodies (HTML error pages, JSON with correlation IDs) must not end up verbatim in
 * logcat or on screen, so the message holds at most [MAX_BODY_CHARS] chars of the body with
 * whitespace collapsed. Request URLs and headers are never included.
 *
 * @property code HTTP status code.
 * @property errorCode machine-readable error from a JSON body: the OAuth `error` string
 *   (e.g. `invalid_grant`) or the Graph-style nested `error.code`
 *   (e.g. `InvalidAuthenticationToken`); null when the body is not JSON or has no error field.
 */
class HttpException(
    val code: Int,
    val errorCode: String?,
    message: String,
) : Exception(message) {
    /** Short, body-free description for UI and routine logging, e.g. `HTTP 400: invalid_grant`. */
    val summary: String
        get() = if (errorCode != null) "HTTP $code: $errorCode" else "HTTP $code"

    companion object {
        const val MAX_BODY_CHARS = 200
        private const val MAX_ERROR_CODE_CHARS = 64
        private val WHITESPACE_REGEX = Regex("\\s+")

        fun fromResponse(
            code: Int,
            body: String?,
        ): HttpException {
            val errorCode = parseErrorCode(body)
            return HttpException(code, errorCode, buildMessage(code, errorCode, body))
        }

        /** Extracts `error` (string) or `error.code` (object) from a JSON body, else null. */
        fun parseErrorCode(body: String?): String? {
            val trimmed = body?.trim().orEmpty()
            if (!trimmed.startsWith("{")) return null
            val raw =
                try {
                    when (val error = JSONObject(trimmed).opt("error")) {
                        is String -> error
                        is JSONObject -> error.opt("code") as? String
                        else -> null
                    }
                } catch (_: JSONException) {
                    null
                }
            return raw
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.take(MAX_ERROR_CODE_CHARS)
                ?.takeIf { it.isNotEmpty() }
        }

        /** `HTTP <code> <errorCode>: <body snippet>` with the snippet collapsed and truncated. */
        fun buildMessage(
            code: Int,
            errorCode: String?,
            body: String?,
        ): String {
            val snippet = body.orEmpty().replace(WHITESPACE_REGEX, " ").trim()
            val truncated =
                if (snippet.length > MAX_BODY_CHARS) {
                    snippet.take(MAX_BODY_CHARS) + "…"
                } else {
                    snippet
                }
            return buildString {
                append("HTTP ").append(code)
                if (!errorCode.isNullOrEmpty()) append(' ').append(errorCode)
                if (truncated.isNotEmpty()) append(": ").append(truncated)
            }
        }
    }
}
