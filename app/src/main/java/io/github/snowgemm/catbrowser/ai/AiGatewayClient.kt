package io.github.snowgemm.catbrowser.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AiToolCall(
    val callId: String,
    val name: String,
    val arguments: JSONObject
)

data class AiGatewayReply(
    val text: String,
    val toolCalls: List<AiToolCall>,
    val responseItems: JSONArray
)

data class AiGatewayHealth(val model: String)

class AiGatewayException(message: String) : Exception(message)

private class ApiResponseException(val status: Int, message: String) : Exception(message)

/** Supabase anonymous session + Edge Function client. No user-entered gateway URL or token. */
class AiGatewayClient(context: Context) {
    private val sessionStore = SupabaseSessionStore(context)

    suspend fun checkConnection(): AiGatewayHealth = withContext(Dispatchers.IO) {
        val session = requireSession()
        val response = try {
            requestJson("GET", SupabaseConfig.CAT_AI_FUNCTION_URL, session.accessToken)
        } catch (error: ApiResponseException) {
            throw AiGatewayException(error.message ?: "Không thể kiểm tra kết nối Cat AI.")
        }
        if (response.optBoolean("ok") != true) throw AiGatewayException("Máy chủ Cat AI chưa xác nhận kết nối.")
        AiGatewayHealth(response.optString("model").ifBlank { "Gemini" })
    }

    suspend fun sendInputItems(inputItems: JSONArray): AiGatewayReply = withContext(Dispatchers.IO) {
        val session = requireSession()
        val response = try {
            requestJson(
                method = "POST",
                endpoint = SupabaseConfig.CAT_AI_FUNCTION_URL,
                accessToken = session.accessToken,
                payload = JSONObject().put("input", inputItems)
            )
        } catch (error: ApiResponseException) {
            throw AiGatewayException(error.message ?: "Không thể gửi yêu cầu tới Cat AI.")
        }
        val steps = response.optJSONArray("responseItems") ?: JSONArray()
        val calls = buildList {
            for (index in 0 until steps.length()) {
                val item = steps.optJSONObject(index) ?: continue
                if (item.optString("type") != "function_call") continue
                val callId = item.optString("id")
                val name = item.optString("name")
                val rawArguments = item.opt("arguments")
                val arguments = when (rawArguments) {
                    is JSONObject -> rawArguments
                    is String -> runCatching { JSONObject(rawArguments) }.getOrNull()
                    null -> null
                    else -> null
                } ?: JSONObject()
                if (callId.isNotBlank() && name.isNotBlank()) add(AiToolCall(callId, name, arguments))
                if (size >= MAX_TOOL_CALLS) break
            }
        }
        AiGatewayReply(
            text = response.optString("outputText").take(MAX_RESPONSE_CHARS),
            toolCalls = calls,
            responseItems = steps
        )
    }

    private suspend fun requireSession(): SupabaseSession = sessionMutex.withLock {
        val now = System.currentTimeMillis() / 1_000
        val saved = sessionStore.load()
        if (saved != null && saved.expiresAtEpochSeconds > now + REFRESH_SKEW_SECONDS) return@withLock saved

        if (saved != null) {
            try {
                val refreshed = refreshSession(saved.refreshToken)
                sessionStore.save(refreshed)
                return@withLock refreshed
            } catch (error: ApiResponseException) {
                if (error.status !in 400..401) {
                    throw AiGatewayException(error.message ?: "Không thể làm mới phiên Supabase.")
                }
                sessionStore.clear()
            }
        }

        val created = createAnonymousSession()
        sessionStore.save(created)
        created
    }

    private fun createAnonymousSession(): SupabaseSession {
        val response = try {
            requestJson(
                method = "POST",
                endpoint = SupabaseConfig.BASE_URL + "/auth/v1/signup",
                payload = JSONObject().put("data", JSONObject())
            )
        } catch (error: ApiResponseException) {
            val message = if (error.status == 400 || error.status == 403 || error.status == 422) {
                "Không thể tạo phiên ẩn danh. Kiểm tra Anonymous Sign-ins trong Supabase Auth."
            } else error.message ?: "Không thể tạo phiên Supabase."
            throw AiGatewayException(message)
        }
        return parseSession(response)
    }

    private fun refreshSession(refreshToken: String): SupabaseSession {
        val response = requestJson(
            method = "POST",
            endpoint = SupabaseConfig.BASE_URL + "/auth/v1/token?grant_type=refresh_token",
            payload = JSONObject().put("refresh_token", refreshToken)
        )
        return parseSession(response, refreshToken)
    }

    private fun parseSession(response: JSONObject, fallbackRefreshToken: String = ""): SupabaseSession {
        val accessToken = response.optString("access_token")
        val refreshToken = response.optString("refresh_token").ifBlank { fallbackRefreshToken }
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            throw AiGatewayException("Supabase không trả về phiên đăng nhập hợp lệ.")
        }
        val now = System.currentTimeMillis() / 1_000
        val expiresAt = response.optLong("expires_at", now + response.optLong("expires_in", 3_600))
        return SupabaseSession(accessToken, refreshToken, expiresAt)
    }

    private fun requestJson(
        method: String,
        endpoint: String,
        accessToken: String? = null,
        payload: JSONObject? = null
    ): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (payload != null) {
                val body = payload.toString().toByteArray(Charsets.UTF_8)
                if (body.size > MAX_REQUEST_BYTES) throw AiGatewayException("Lịch sử hội thoại quá lớn.")
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        output.append(buffer, 0, count)
                        if (output.length > MAX_RESPONSE_CHARS) {
                            throw AiGatewayException("Phản hồi từ Cat AI vượt quá giới hạn.")
                        }
                    }
                    output.toString()
                }
            }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull() ?: JSONObject()

            if (status !in 200..299) {
                val message = when (status) {
                    401 -> "Phiên Cat AI không hợp lệ. Hãy thử lại."
                    403 -> "Phiên này không được phép truy cập Cat AI."
                    429 -> {
                        val retry = response.optInt("retryAfterSeconds", 0)
                        if (retry > 0) "Cat AI đang giới hạn lượt gọi. Thử lại sau khoảng $retry giây."
                        else "Cat AI đang giới hạn lượt gọi. Hãy thử lại sau."
                    }
                    503 -> response.optString("error").ifBlank { "Cat AI chưa được cấu hình đầy đủ trên máy chủ." }
                    else -> response.optString("error").ifBlank { "Máy chủ Cat AI trả về HTTP $status." }
                }
                throw ApiResponseException(status, message.take(240))
            }
            return response
        } catch (error: AiGatewayException) {
            throw error
        } catch (error: ApiResponseException) {
            throw error
        } catch (_: java.net.SocketTimeoutException) {
            throw AiGatewayException("Kết nối Cat AI quá thời gian chờ. Hãy thử lại.")
        } catch (_: java.io.IOException) {
            throw AiGatewayException("Không thể kết nối Supabase qua HTTPS. Kiểm tra mạng.")
        } catch (_: Exception) {
            throw AiGatewayException("Không thể xử lý phản hồi từ máy chủ Cat AI.")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        val sessionMutex = Mutex()
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 45_000
        const val REFRESH_SKEW_SECONDS = 90L
        const val MAX_REQUEST_BYTES = 28 * 1024
        const val MAX_RESPONSE_CHARS = 48 * 1024
        const val MAX_TOOL_CALLS = 8
    }
}
