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
class AiGatewayClient {
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
                val message = response?.optString("error").orEmpty()
                    .ifBlank { "Gateway AI trả về mã lỗi $status." }
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
        const val MAX_REQUEST_BYTES = 28 * 1024
        const val MAX_RESPONSE_CHARS = 48 * 1024
        const val MAX_TOOL_CALLS = 5
    }
}
