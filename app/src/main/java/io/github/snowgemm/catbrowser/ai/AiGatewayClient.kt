package io.github.snowgemm.catbrowser.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AiChatMessage(
    val role: String,
    val content: String
)

data class AiToolCall(
    val callId: String,
    val name: String,
    val arguments: JSONObject
)

data class AiToolOutput(
    val callId: String,
    val output: String
)

data class AiGatewayReply(
    val text: String,
    val toolCalls: List<AiToolCall>,
    val responseItems: JSONArray
)

class AiGatewayException(message: String) : Exception(message)

/**
 * Calls Cat's self-hosted gateway. The OpenAI key stays on the server.
 * Conversation items are replayed with store=false instead of creating
 * a persistent OpenAI response chain.
 */
data class AiGatewayHealth(
    val model: String
)

class AiGatewayClient {
    suspend fun checkConnection(config: AiGatewayConfig): AiGatewayHealth =
        withContext(Dispatchers.IO) {
            val endpoint = config.baseUrl.trimEnd('/') + API_PATH
            var connection: HttpURLConnection? = null
            try {
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = HEALTH_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer " + config.accessToken)

                val status = connection.responseCode
                if (status in 300..399) {
                    throw AiGatewayException(
                        "Gateway đang chuyển hướng hoặc bị Vercel SSO bảo vệ. Hãy dùng custom domain HTTPS đã cấu hình."
                    )
                }
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrNull()
                if (status !in 200..299) {
                    val message = when (status) {
                        401 -> "Gateway token không chính xác."
                        429 -> "Gateway đang giới hạn yêu cầu. Thử lại sau."
                        503 -> "Gateway chưa có đủ biến môi trường bí mật."
                        else -> response?.optString("error").orEmpty()
                            .ifBlank { "Gateway trả về HTTP $status." }
                    }
                    throw AiGatewayException(message.take(240))
                }
                if (response?.optBoolean("ok") != true) {
                    throw AiGatewayException("Gateway trả về trạng thái không hợp lệ.")
                }
                val model = response.optString("model").takeIf { it.isNotBlank() }
                    ?: throw AiGatewayException("Gateway không xác nhận model OpenAI.")
                AiGatewayHealth(model)
            } catch (error: AiGatewayException) {
                throw error
            } catch (_: java.net.SocketTimeoutException) {
                throw AiGatewayException("Kiểm tra kết nối quá thời gian chờ.")
            } catch (_: java.io.IOException) {
                throw AiGatewayException("Không thể truy cập Gateway qua HTTPS.")
            } catch (_: Exception) {
                throw AiGatewayException("Không thể kiểm tra cấu hình Gateway.")
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun sendInputItems(
        config: AiGatewayConfig,
        inputItems: JSONArray
    ): AiGatewayReply = withContext(Dispatchers.IO) {
        post(config, JSONObject().put("inputItems", inputItems))
    }

    private fun post(config: AiGatewayConfig, payload: JSONObject): AiGatewayReply {
        val endpoint = config.baseUrl.trimEnd('/') + API_PATH
        var connection: HttpURLConnection? = null
        try {
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer " + config.accessToken)

            val body = payload.toString().toByteArray(Charsets.UTF_8)
            if (body.size > MAX_REQUEST_BYTES) throw AiGatewayException("Lịch sử hội thoại quá lớn.")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

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
                            throw AiGatewayException("Phản hồi từ AI vượt quá giới hạn.")
                        }
                    }
                    output.toString()
                }
            }.orEmpty()

            val response = runCatching { JSONObject(responseText) }.getOrNull()
            if (status !in 200..299) {
                val message = if (status == 429) {
                    when (response?.optString("code")) {
                        "insufficient_quota" ->
                            "Hạn mức hoặc giới hạn thanh toán OpenAI API đã hết. Cần kiểm tra Usage và Billing của API project."
                        "rate_limit_exceeded" -> {
                            val retryAfter = response?.optInt("retryAfterSeconds", 0) ?: 0
                            if (retryAfter > 0) {
                                "OpenAI đang giới hạn tốc độ yêu cầu. Hãy đợi khoảng $retryAfter giây rồi thử lại."
                            } else {
                                "OpenAI đang giới hạn tốc độ yêu cầu. Hãy đợi một lúc rồi thử lại."
                            }
                        }
                        "gateway_rate_limited" ->
                            "Cat AI Gateway đang giới hạn số yêu cầu. Hãy đợi khoảng một phút rồi thử lại."
                        else -> "Cat AI Gateway trả về HTTP 429. Hãy thử lại sau."
                    }
                } else {
                    response?.optString("error").orEmpty()
                        .ifBlank { "Gateway AI trả về mã lỗi $status." }
                }
                throw AiGatewayException(message.take(240))
            }
            if (response == null) throw AiGatewayException("Phản hồi gateway không hợp lệ.")

            val calls = buildList {
                val array = response.optJSONArray("toolCalls") ?: JSONArray()
                for (index in 0 until array.length().coerceAtMost(MAX_TOOL_CALLS)) {
                    val item = array.optJSONObject(index) ?: continue
                    val callId = item.optString("callId")
                    val name = item.optString("name")
                    val arguments = item.optJSONObject("arguments") ?: JSONObject()
                    if (callId.isNotBlank() && name.isNotBlank()) {
                        add(AiToolCall(callId, name, arguments))
                    }
                }
            }
            return AiGatewayReply(
                text = response.optString("outputText").take(MAX_RESPONSE_CHARS),
                toolCalls = calls,
                responseItems = response.optJSONArray("responseItems") ?: JSONArray()
            )
        } catch (error: AiGatewayException) {
            throw error
        } catch (_: java.net.SocketTimeoutException) {
            throw AiGatewayException("Kết nối AI quá thời gian chờ. Hãy thử lại.")
        } catch (_: java.io.IOException) {
            throw AiGatewayException("Không thể kết nối gateway AI. Kiểm tra mạng và địa chỉ máy chủ.")
        } catch (_: Exception) {
            throw AiGatewayException("Không thể xử lý phản hồi từ gateway AI.")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val API_PATH = "/api/assistant"
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 45_000
        const val HEALTH_TIMEOUT_MS = 12_000
        const val MAX_REQUEST_BYTES = 28 * 1024
        const val MAX_RESPONSE_CHARS = 48 * 1024
        const val MAX_TOOL_CALLS = 5
    }
}
