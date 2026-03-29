package com.squads.app.ui.mail

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.squads.app.data.MailFolder
import com.squads.app.data.MailMessage
import com.squads.app.data.toRelativeTime
import com.squads.app.ui.components.Avatar
import com.squads.app.ui.components.ImportanceBadge
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.ScreenHeader
import com.squads.app.ui.components.UnreadBadge
import com.squads.app.ui.theme.BottomNavHeight
import com.squads.app.viewmodel.MailViewModel

@Composable
fun MailScreen(
    viewModel: MailViewModel = hiltViewModel(),
    onMailClick: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading && messages.isEmpty() && folders.isEmpty()) {
        LoadingScreen()
    } else {
        MailListScreen(
            messages = messages,
            folders = folders,
            currentFolderId = currentFolderId,
            onFolderClick = { viewModel.switchFolder(it) },
            onMailClick = { mail ->
                viewModel.selectMail(mail)
                onMailClick()
            },
        )
    }
}

@Composable
private fun MailListScreen(
    messages: List<MailMessage>,
    folders: List<MailFolder>,
    currentFolderId: String?,
    onFolderClick: (String) -> Unit,
    onMailClick: (MailMessage) -> Unit,
) {
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomNavHeight + systemNavInset),
    ) {
        item { ScreenHeader("Mail") }
        if (folders.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = folder.id == currentFolderId,
                            onClick = { onFolderClick(folder.id) },
                            label = { Text(folder.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(messages, key = { it.id }, contentType = { "mail" }) { mail ->
            MailRow(mail = mail, onClick = { onMailClick(mail) })
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun MailRow(
    mail: MailMessage,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(name = mail.fromName)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = mail.fromName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!mail.isRead) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (mail.hasAttachments) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = mail.receivedDateTime.toRelativeTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ImportanceBadge(mail.importance)
                if (mail.importance == "high") Spacer(Modifier.width(6.dp))
                Text(
                    text = mail.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!mail.isRead) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mail.bodyPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!mail.isRead) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    mail: MailMessage,
    onBack: () -> Unit,
    isBodyLoading: Boolean = false,
    tokenProvider: (suspend (String) -> String?)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mail.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = mail.fromName, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(mail.fromName, fontWeight = FontWeight.SemiBold)
                        Text(
                            mail.fromAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        mail.receivedDateTime.toRelativeTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
            }

            when {
                isBodyLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                mail.body.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No content",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    MailBodyWebView(
                        html = mail.body,
                        tokenProvider = tokenProvider,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MailBodyWebView(
    html: String,
    tokenProvider: (suspend (String) -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val bgColor = MaterialTheme.colorScheme.background
    val linkColor = MaterialTheme.colorScheme.primary
    val textHex = remember(textColor) { String.format("#%06X", textColor.toArgb() and 0xFFFFFF) }
    val bgHex = remember(bgColor) { String.format("#%06X", bgColor.toArgb() and 0xFFFFFF) }
    val linkHex = remember(linkColor) { String.format("#%06X", linkColor.toArgb() and 0xFFFFFF) }
    val bgArgb = remember(bgColor) { bgColor.toArgb() }

    // Strip outer <html>/<head>/<body> tags so our wrapper CSS applies cleanly
    val bodyContent =
        remember(html) {
            BODY_TAG_REGEX
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.trim() ?: html
        }

    val styledHtml =
        remember(bodyContent, textHex, bgHex, linkHex) {
            """
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { color: $textHex !important; background: $bgHex !important; font-family: sans-serif; font-size: 15px; line-height: 1.5; padding: 16px; padding-bottom: 64px; margin: 0; word-wrap: break-word; }
                * { color: inherit !important; background-color: transparent !important; }
                a { color: $linkHex !important; }
                img { max-width: 100% !important; height: auto !important; }
                table { max-width: 100%; overflow-x: auto; }
                pre, code { white-space: pre-wrap; max-width: 100%; }
            </style>
            </head><body>$bodyContent</body></html>
            """.trimIndent()
        }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(bgArgb)
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            view?.clearHistory()
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            request?.url?.let { uri ->
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            val provider = tokenProvider ?: return null
                            if ("graph.microsoft.com" !in url && "teams.microsoft.com" !in url) return null
                            val conn =
                                try {
                                    val token = kotlinx.coroutines.runBlocking { provider(url) } ?: return null
                                    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                    c.setRequestProperty("Authorization", "Bearer $token")
                                    c.connect()
                                    c
                                } catch (_: Exception) {
                                    return null
                                }
                            return try {
                                if (conn.responseCode != 200) {
                                    conn.disconnect()
                                    return null
                                }
                                val contentType = conn.contentType?.substringBefore(";") ?: "image/png"
                                val stream =
                                    object : java.io.FilterInputStream(conn.inputStream) {
                                        override fun close() {
                                            super.close()
                                            conn.disconnect()
                                        }
                                    }
                                WebResourceResponse(contentType, "UTF-8", stream)
                            } catch (_: Exception) {
                                conn.disconnect()
                                null
                            }
                        }
                    }
                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.setBackgroundColor(bgArgb)
            if (webView.tag != styledHtml) {
                webView.tag = styledHtml
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier,
    )
}

private val BODY_TAG_REGEX = Regex("""<body[^>]*>([\s\S]*)</body>""", RegexOption.IGNORE_CASE)
