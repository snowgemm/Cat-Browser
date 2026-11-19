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
    val responseId: String?,
    val text: String,
    val toolCalls: List<AiToolCall>
)

class AiGatewayException(message: String) : Exception(message)

/** Client for Cat's self-hosted gateway. It never receives an OpenAI API key. */
class AiGatewayClient {
    suspend fun sendMessages(
        config: AiGatewayConfig,
        messages: List<AiChatMessage>
    ): AiGatewayReply = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().put("role", message.role).put("content", message.content))
            }
        })
        post(config, payload)
    }

    suspend fun sendNextMessage(
        config: AiGatewayConfig,
        previousResponseId: String,
        message: String
    ): AiGatewayReply = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("previousResponseId", previousResponseId)
            .put("inputMessage", message)
        post(config, payload)
    }

    suspend fun sendToolOutputs(
        config: AiGatewayConfig,
        previousResponseId: String,
        outputs: List<AiToolOutput>
    ): AiGatewayReply = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("previousResponseId", previousResponseId)
            .put("toolOutputs", JSONArray().apply {
                outputs.forEach { item ->
                    put(
                        JSONObject()
                            .put("callId", item.callId)
                            .put("output", item.output.take(MAX_TOOL_OUTPUT_CHARS))
                    )
                }
            })
        post(config, payload)
    }

    private fun post(config: AiGatewayConfig, payload: JSONObject): AiGatewayReply {
        val endpoint = config.baseUrl.trimEnd('/') + API_PATH
        var connection: HttpURLConnection? = null
        try {
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer " + config.accessToken)
            val body = payload.toString().toByteArray(Charsets.UTF_8)
            if (body.size > MAX_REQUEST_BYTES) throw AiGatewayException("Tin nhắn gửi đi quá lớn.")
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
                responseId = response.optString("responseId").takeIf { it.isNotBlank() },
                text = response.optString("outputText").take(MAX_RESPONSE_CHARS),
                toolCalls = calls
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
        const val MAX_REQUEST_BYTES = 24 * 1024
        const val MAX_RESPONSE_CHARS = 48 * 1024
        const val MAX_TOOL_CALLS = 5
        const val MAX_TOOL_OUTPUT_CHARS = 2_000
    }
}
