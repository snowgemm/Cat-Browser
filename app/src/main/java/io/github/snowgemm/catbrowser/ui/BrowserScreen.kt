package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.snowgemm.catbrowser.browser.BrowserUrlResolver
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private const val START_PAGE = "https://duckduckgo.com/"

private val CatColorScheme = darkColorScheme(
    primary = Color(0xFF72D7A2),
    onPrimary = Color(0xFF062417),
    secondary = Color(0xFFB6D9C2),
    onSecondary = Color(0xFF14251B),
    background = Color(0xFF101612),
    onBackground = Color(0xFFE7F0E9),
    surface = Color(0xFF151E18),
    onSurface = Color(0xFFE7F0E9),
    surfaceVariant = Color(0xFF202B24),
    onSurfaceVariant = Color(0xFFB5C2B8),
    outline = Color(0xFF53645A)
)

@Composable
fun CatBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CatColorScheme, content = content)
}

@Composable
fun BrowserScreen(runtime: GeckoRuntime) {
    var addressText by rememberSaveable { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var sessionReady by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val session = remember(runtime) { GeckoSession() }

    DisposableEffect(session, runtime) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (!url.isNullOrBlank()) addressText = url
            }

            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                canGoBack = value
            }

            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                canGoForward = value
            }
        }

        session.open(runtime)
        sessionReady = true

        onDispose {
            sessionReady = false
            session.navigationDelegate = null
            session.close()
        }
    }

    LaunchedEffect(sessionReady, session) {
        if (sessionReady) {
            session.load(GeckoSession.Loader().uri(START_PAGE).originalInput(START_PAGE))
        }
    }

    fun navigate() {
        val destination = BrowserUrlResolver.resolve(addressText) ?: return
        keyboardController?.hide()
        focusManager.clearFocus()
        session.load(
            GeckoSession.Loader()
                .uri(destination)
                .originalInput(addressText.trim())
        )
    }

    BackHandler(enabled = canGoBack) {
        session.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CAT BROWSER",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "GECKO",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { newValue -> addressText = newValue },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(
                onClick = { navigate() },
                enabled = addressText.isNotBlank() && sessionReady,
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text("Go")
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (sessionReady) {
                AndroidView(
                    factory = { context -> GeckoView(context).apply { setSession(session) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Starting browser engine…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationAction("Back", "‹", canGoBack) { session.goBack() }
            NavigationAction("Forward", "›", canGoForward) { session.goForward() }
            NavigationAction("Home", "⌂", sessionReady) {
                session.load(GeckoSession.Loader().uri(START_PAGE))
            }
            NavigationAction("Reload", "↻", sessionReady) {
                session.reload()
            }
        }
    }
}

@Composable
private fun NavigationAction(
    label: String,
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = symbol,
            fontSize = 24.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
