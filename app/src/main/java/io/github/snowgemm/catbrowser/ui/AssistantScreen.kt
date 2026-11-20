package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.ai.AiGatewayClient
import io.github.snowgemm.catbrowser.ai.AiGatewayException
import io.github.snowgemm.catbrowser.ai.AiToolCall
import io.github.snowgemm.catbrowser.browser.AssistantTabSnapshot
import io.github.snowgemm.catbrowser.browser.BrowserController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AssistantMessage(
    val id: Long,
    val role: String,
    val text: String
)

@Composable
internal fun AssistantScreen(
    controller: BrowserController,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val client = remember { AiGatewayClient(context) }
    val messages = remember { mutableStateListOf<AssistantMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }

    var draft by remember { mutableStateOf("") }
    var inputItems by remember { mutableStateOf(JSONArray()) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCalls by remember { mutableStateOf<List<AiToolCall>>(emptyList()) }
    var hasPendingToolApproval by remember { mutableStateOf(false) }
    var toolRounds by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (hasPendingToolApproval) {
            resolvePendingTool(
                approved = false,
                calls = pendingCalls,
                inputItems = inputItems,
                controller = controller,
                onUpdatedItems = { inputItems = it },
                onUpdatedCalls = { pendingCalls = it },
                onApprovalStateChanged = { hasPendingToolApproval = it },
                onActivity = { text -> messages.add(AssistantMessage(System.nanoTime(), "activity", text)) },
                onContinue = {
                    continueAssistant(
                        client = client,
                        initialItems = inputItems,
                        scope = scope,
                        onUpdatedItems = { inputItems = it },
                        onMessages = { text -> messages.add(AssistantMessage(System.nanoTime(), "assistant", text)) },
                        onPendingCalls = {
                            pendingCalls = it
                            hasPendingToolApproval = it.isNotEmpty()
                        },
                        onBusy = { isBusy = it },
                        onError = { errorMessage = it },
                        toolRoundsSoFar = toolRounds,
                        onToolRounds = { toolRounds = it }
                    )
                }
            )
        } else {
            onBack()
        }
    }

    LaunchedEffect(messages.size, pendingCalls.size) {
        val count = messages.size
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun submitMessage() {
        val text = draft.trim()
        if (text.isBlank() || isBusy || hasPendingToolApproval || text.length > MAX_USER_MESSAGE_CHARS) return

        val nextItems = JSONArray(inputItems.toString()).put(
            JSONObject()
                .put("type", "user_input")
                .put("content", JSONArray().put(JSONObject()
                    .put("type", "text")
                    .put("text", text)))
        )
        inputItems = nextItems
        messages.add(AssistantMessage(System.nanoTime(), "user", text))
        draft = ""
        errorMessage = null
        keyboard?.hide()

        continueAssistant(
            client = client,
            initialItems = nextItems,
            scope = scope,
            onUpdatedItems = { inputItems = it },
            onMessages = { reply -> messages.add(AssistantMessage(System.nanoTime(), "assistant", reply)) },
            onPendingCalls = {
                pendingCalls = it
                hasPendingToolApproval = it.isNotEmpty()
            },
            onBusy = { isBusy = it },
            onError = { errorMessage = it },
            toolRoundsSoFar = toolRounds,
                        onToolRounds = { toolRounds = it }
        )
    }

    fun handleToolDecision(approved: Boolean) {
        resolvePendingTool(
            approved = approved,
            calls = pendingCalls,
            inputItems = inputItems,
            controller = controller,
            onUpdatedItems = { inputItems = it },
            onUpdatedCalls = { pendingCalls = it },
            onApprovalStateChanged = { hasPendingToolApproval = it },
            onActivity = { text -> messages.add(AssistantMessage(System.nanoTime(), "activity", text)) },
            onContinue = {
                continueAssistant(
                    client = client,
                    initialItems = inputItems,
                    scope = scope,
                    onUpdatedItems = { inputItems = it },
                    onMessages = { text -> messages.add(AssistantMessage(System.nanoTime(), "assistant", text)) },
                    onPendingCalls = {
                        pendingCalls = it
                        hasPendingToolApproval = it.isNotEmpty()
                    },
                    onBusy = { isBusy = it },
                    onError = { errorMessage = it },
                    toolRoundsSoFar = toolRounds,
                        onToolRounds = { toolRounds = it }
                )
            }
        )
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  Quay lại") }
            Column(Modifier.weight(1f)) {
                Text("Cat AI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Trợ lý Google Gemini", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            TextButton(
                enabled = !isBusy && !hasPendingToolApproval && messages.isNotEmpty(),
                onClick = { showClearDialog = true }
            ) { Text("Chat mới") }
        }

        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    "Riêng tư theo mặc định",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tin nhắn được gửi tới Google Gemini qua Supabase. Cat không tự gửi nội dung trang, cookie hoặc tab ẩn danh; dữ liệu tab chỉ được gửi khi bạn duyệt thao tác liên quan. Mọi thao tác trình duyệt đều cần bạn cho phép.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Bạn muốn Cat giúp gì?",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ví dụ: “Mở trang example.com trong tab mới” hoặc “Liệt kê các tab thường đang mở”.",
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    AssistantMessageBubble(message)
                }
                if (isBusy) {
                    item {
                        Text("Cat AI đang xử lý…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp)
                    }
                }
                errorMessage?.let { error ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                TextButton(
                                    enabled = !isBusy && !hasPendingToolApproval && inputItems.length() > 0,
                                    onClick = {
                                        errorMessage = null
                                        continueAssistant(
                                            client = client,
                                            initialItems = inputItems,
                                            scope = scope,
                                            onUpdatedItems = { inputItems = it },
                                            onMessages = { text ->
                                                messages.add(AssistantMessage(System.nanoTime(), "assistant", text))
                                            },
                                            onPendingCalls = {
                                                pendingCalls = it
                                                hasPendingToolApproval = it.isNotEmpty()
                                            },
                                            onBusy = { isBusy = it },
                                            onError = { errorMessage = it },
                                            toolRoundsSoFar = toolRounds,
                        onToolRounds = { toolRounds = it }
                                        )
                                    }
                                ) { Text("Thử lại") }
                            }
                        }
                    }
                }
            }

            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(MAX_USER_MESSAGE_CHARS) },
                        modifier = Modifier.weight(1f).focusRequester(inputFocus),
                        placeholder = { Text("Nhắn Cat AI…") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !isBusy && !hasPendingToolApproval,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitMessage() })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = ::submitMessage,
                        enabled = draft.isNotBlank() && !isBusy && !hasPendingToolApproval,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) { Text("Gửi") }
                }
            }
    }

    val currentCall = pendingCalls.firstOrNull()
    if (hasPendingToolApproval && currentCall != null) {
        AlertDialog(
            onDismissRequest = { handleToolDecision(false) },
            title = { Text("Cho phép Cat thực hiện?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(toolTitle(currentCall.name), fontWeight = FontWeight.SemiBold)
                    Text(
                        toolSummary(currentCall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Chỉ thao tác này được duyệt. Các thao tác khác sẽ hỏi riêng.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { handleToolDecision(true) }) { Text("Cho phép") }
            },
            dismissButton = {
                TextButton(onClick = { handleToolDecision(false) }) { Text("Từ chối") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Bắt đầu cuộc trò chuyện mới?") },
            text = { Text("Tin nhắn và ngữ cảnh AI hiện tại sẽ bị xóa khỏi bộ nhớ của màn hình này.") },
            confirmButton = {
                TextButton(onClick = {
                    inputItems = JSONArray()
                    messages.clear()
                    errorMessage = null
                    pendingCalls = emptyList()
                    hasPendingToolApproval = false
                    toolRounds = 0
                    showClearDialog = false
                }) { Text("Xóa cuộc trò chuyện") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun AssistantMessageBubble(message: AssistantMessage) {
    val isUser = message.role == "user"
    val isActivity = message.role == "activity"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 17.dp,
                topEnd = 17.dp,
                bottomEnd = if (isUser) 5.dp else 17.dp,
                bottomStart = if (isUser) 17.dp else 5.dp
            ),
            color = when {
                isActivity -> MaterialTheme.colorScheme.surfaceVariant
                isUser -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                color = when {
                    isUser -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = if (isActivity) 10.sp else 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

private fun continueAssistant(
    client: AiGatewayClient,
    initialItems: JSONArray,
    scope: kotlinx.coroutines.CoroutineScope,
    onUpdatedItems: (JSONArray) -> Unit,
    onMessages: (String) -> Unit,
    onPendingCalls: (List<AiToolCall>) -> Unit,
    onBusy: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    toolRoundsSoFar: Int,
    onToolRounds: (Int) -> Unit
) {
    if (onToolRoundsValueOverflow(initialItems)) {
        onError("Cuộc trò chuyện quá dài. Hãy bắt đầu một chat mới.")
        return
    }
    onBusy(true)
    onError(null)
    scope.launch {
        try {
            var items = JSONArray(initialItems.toString())
            val reply = client.sendInputItems(items)
            if (reply.toolCalls.isNotEmpty() && toolRoundsSoFar >= MAX_TOOL_ROUNDS) {
                onMessages("Cat AI đã đạt giới hạn số vòng thao tác cho yêu cầu này. Hãy bắt đầu chat mới nếu cần tiếp tục.")
                onToolRounds(0)
                onBusy(false)
                return@launch
            }
            val updated = JSONArray(items.toString())
            for (index in 0 until reply.responseItems.length()) {
                updated.put(reply.responseItems.get(index))
            }
            items = updated
            onUpdatedItems(items)
            if (reply.text.isNotBlank()) onMessages(reply.text)
            if (reply.toolCalls.isNotEmpty()) {
                onToolRounds(toolRoundsSoFar + 1)
                onPendingCalls(reply.toolCalls)
                onBusy(false)
                return@launch
            }
            if (reply.text.isBlank()) onMessages("Mình chưa nhận được câu trả lời. Bạn có thể thử diễn đạt lại.")
            onToolRounds(0)
            onBusy(false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: AiGatewayException) {
            onError(error.message ?: "Không thể kết nối Cat AI.")
            onBusy(false)
        } catch (_: Exception) {
            onError("Có lỗi khi xử lý cuộc trò chuyện. Hãy thử lại.")
            onBusy(false)
        }
    }
}

private fun onToolRoundsValueOverflow(items: JSONArray): Boolean = items.length() >= MAX_INPUT_ITEMS

private fun resolvePendingTool(
    approved: Boolean,
    calls: List<AiToolCall>,
    inputItems: JSONArray,
    controller: BrowserController,
    onUpdatedItems: (JSONArray) -> Unit,
    onUpdatedCalls: (List<AiToolCall>) -> Unit,
    onApprovalStateChanged: (Boolean) -> Unit,
    onActivity: (String) -> Unit,
    onContinue: () -> Unit
) {
    val call = calls.firstOrNull() ?: return
    val output = if (approved) executeApprovedTool(call, controller) else {
        JSONObject().put("ok", false).put("message", "Người dùng đã từ chối thao tác này.")
    }

    val updated = JSONArray(inputItems.toString()).put(
        JSONObject()
            .put("type", "function_result")
            .put("name", call.name)
            .put("call_id", call.callId)
            .put("result", JSONArray().put(JSONObject()
                .put("type", "text")
                .put("text", output.toString())))
    )
    onUpdatedItems(updated)
    onActivity(
        if (approved) "Đã gửi kết quả thao tác “" + toolTitle(call.name) + "” cho Cat AI."
        else "Bạn đã từ chối thao tác “" + toolTitle(call.name) + "”."
    )

    val remaining = calls.drop(1)
    onUpdatedCalls(remaining)
    if (remaining.isEmpty()) {
        onApprovalStateChanged(false)
        onContinue()
    }
}

private fun executeApprovedTool(call: AiToolCall, controller: BrowserController): JSONObject {
    val args = call.arguments
    return when (call.name) {
        "list_tabs" -> {
            val tabs = JSONArray()
            controller.assistantListTabs().forEach { tab -> tabs.put(tab.toJson()) }
            JSONObject().put("ok", true).put("tabs", tabs)
        }
        "get_active_tab" -> {
            val tab = controller.assistantActiveTab()
            if (tab == null) JSONObject().put("ok", false)
                .put("message", "Không có tab thường đang chọn. Tab ẩn danh không được chia sẻ.")
            else JSONObject().put("ok", true).put("tab", tab.toJson())
        }
        "open_url" -> {
            val url = args.optString("url")
            val id = controller.assistantOpenUrlInNewTab(url)
            JSONObject().put("ok", id != null).put("tabId", id ?: "")
                .put("message", if (id != null) "Đã mở website trong tab thường mới." else "URL không hợp lệ hoặc không thể tạo tab.")
        }
        "switch_tab" -> {
            val switched = controller.assistantSwitchToTab(args.optString("tab_id"))
            JSONObject().put("ok", switched)
                .put("message", if (switched) "Đã chuyển tab." else "Không tìm thấy tab thường.")
        }
        "go_back" -> {
            val moved = controller.assistantGoBack()
            JSONObject().put("ok", moved).put("message",
                if (moved) "Đã gửi lệnh quay lại." else "Tab thường không có trang trước để mở.")
        }
        "go_forward" -> {
            val moved = controller.assistantGoForward()
            JSONObject().put("ok", moved).put("message",
                if (moved) "Đã gửi lệnh tiến tới." else "Tab thường không có trang tiếp theo.")
        }
        "go_home" -> {
            val opened = controller.assistantGoHome()
            JSONObject().put("ok", opened).put("message",
                if (opened) "Đã yêu cầu mở trang chủ." else "Tab ẩn danh không được điều khiển bởi Cat AI.")
        }
        "bookmark_current_page" -> {
            val saved = controller.assistantBookmarkCurrentPage()
            JSONObject().put("ok", saved).put("message",
                if (saved) "Trang đã có trong dấu trang hoặc vừa được lưu." else "Không thể lưu trang này vào dấu trang.")
        }
        else -> JSONObject().put("ok", false).put("message", "Công cụ không được hỗ trợ.")
    }
}

private fun AssistantTabSnapshot.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title.take(64))
    .put("url", url.take(128))
    .put("active", active)

private fun toolTitle(name: String): String = when (name) {
    "list_tabs" -> "Liệt kê tab thường"
    "get_active_tab" -> "Xem tab thường đang chọn"
    "open_url" -> "Mở website trong tab mới"
    "switch_tab" -> "Chuyển sang tab"
    "go_back" -> "Quay lại"
    "go_forward" -> "Tiến tới"
    "go_home" -> "Mở trang chủ"
    "bookmark_current_page" -> "Thêm trang hiện tại vào dấu trang"
    else -> "Thao tác trình duyệt"
}

private fun toolSummary(call: AiToolCall): String = when (call.name) {
    "open_url" -> "Mở địa chỉ: " + call.arguments.optString("url").take(180)
    "switch_tab" -> "Chuyển sang tab ID: " + call.arguments.optString("tab_id").take(80)
    "list_tabs" -> "Cat AI muốn xem tên và địa chỉ các tab thường đang mở. Tab ẩn danh bị loại bỏ."
    "get_active_tab" -> "Cat AI muốn xem tên và địa chỉ tab thường đang chọn. Không chia sẻ nội dung trang."
    "bookmark_current_page" -> "Lưu địa chỉ của trang thường hiện tại vào dấu trang."
    else -> "Cat AI muốn dùng một thao tác trình duyệt có giới hạn."
}

private const val MAX_USER_MESSAGE_CHARS = 4_000
private const val MAX_INPUT_ITEMS = 32
private const val MAX_TOOL_ROUNDS = 4
