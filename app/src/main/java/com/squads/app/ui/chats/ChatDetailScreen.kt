package com.squads.app.ui.chats

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.squads.app.data.ChatMessage
import com.squads.app.data.graphProfilePhotoUrl
import com.squads.app.data.toTimeString
import com.squads.app.ui.components.Avatar
import com.squads.app.ui.components.GroupAvatar
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.ReactionChip
import com.squads.app.viewmodel.ChatsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
) {
    val chat by viewModel.selectedChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val messagesLoading by viewModel.messagesLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val showScrollToBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex <= 3) {
            listState.animateScrollToItem(0)
        }
    }

    val currentChat = chat

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (currentChat != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!currentChat.isOneOnOne && currentChat.memberIds.size >= 2) {
                                GroupAvatar(
                                    names = currentChat.memberNames,
                                    size = 36.dp,
                                    photoUrls = currentChat.memberIds.map { graphProfilePhotoUrl(it) },
                                )
                            } else {
                                Avatar(
                                    name = currentChat.title,
                                    size = 36.dp,
                                    isGroup = !currentChat.isOneOnOne,
                                    photoUrl = currentChat.memberId?.let { graphProfilePhotoUrl(it) },
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    currentChat.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (currentChat.isOneOnOne) {
                                        "Chat"
                                    } else {
                                        "${currentChat.memberCount} members"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            if (messagesLoading && messages.isEmpty()) {
                LoadingScreen(Modifier.weight(1f))
            } else if (messages.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Start the conversation!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            } else {
                val reversedMessages = remember(messages) { messages.reversed() }

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                        state = listState,
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item { Spacer(Modifier.height(16.dp)) }

                        reversedMessages.forEachIndexed { index, msg ->
                            val prevMsg = reversedMessages.getOrNull(index + 1)
                            val nextMsg = reversedMessages.getOrNull(index - 1)

                            val isFirstInGroup =
                                prevMsg == null ||
                                    prevMsg.senderId != msg.senderId ||
                                    prevMsg.timestamp.toLocalDate() != msg.timestamp.toLocalDate()

                            item(key = msg.id, contentType = "message") {
                                MessageRow(
                                    msg = msg,
                                    senderPhotoUrl = graphProfilePhotoUrl(msg.senderObjectId),
                                    isFirstInGroup = isFirstInGroup,
                                    onImageClick = { url -> fullscreenImageUrl = url },
                                )
                            }

                            if (prevMsg != null && prevMsg.timestamp.toLocalDate() != msg.timestamp.toLocalDate()) {
                                item(key = "date-${msg.timestamp.toLocalDate()}", contentType = "dateSeparator") {
                                    DateSeparator(msg.timestamp.toLocalDate())
                                }
                            }

                            if (prevMsg == null) {
                                item(key = "date-first-${msg.timestamp.toLocalDate()}", contentType = "dateSeparator") {
                                    DateSeparator(msg.timestamp.toLocalDate())
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            elevation =
                                FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 3.dp,
                                ),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            MessageInput(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(chat?.id ?: "", inputText)
                        inputText = ""
                    }
                },
            )
        }
    }

    // Fullscreen image viewer
    if (fullscreenImageUrl != null) {
        ImageViewer(
            imageUrl = fullscreenImageUrl!!,
            onDismiss = { fullscreenImageUrl = null },
        )
    }
}

@Composable
private fun ImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState =
        rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            if (scale > 1f) {
                offsetX += panChange.x
                offsetY += panChange.y
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }

    BackHandler(onBack = onDismiss)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Full screen image",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ).transformable(state = transformableState)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
        )

        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DateSeparator(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val label =
        when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a message",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                keyboardActions = KeyboardActions.Default,
            )
            Spacer(Modifier.width(6.dp))
            SmallFloatingActionButton(
                onClick = onSend,
                containerColor =
                    if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                contentColor =
                    if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                shape = CircleShape,
                elevation =
                    FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: ChatMessage,
    senderPhotoUrl: String? = null,
    isFirstInGroup: Boolean = true,
    onImageClick: (String) -> Unit = {},
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val textColorArgb = remember(textColor) { textColor.toArgb() }
    val linkColor = MaterialTheme.colorScheme.primary
    val linkColorArgb = remember(linkColor) { linkColor.toArgb() }
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val context = LocalContext.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val clip = android.content.ClipData.newPlainText("message", msg.content)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                ).padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isFirstInGroup) 10.dp else 1.dp,
                    bottom = 1.dp,
                ),
    ) {
        Box(modifier = Modifier.width(40.dp)) {
            if (isFirstInGroup) {
                Avatar(
                    name = msg.senderName,
                    size = 32.dp,
                    photoUrl = senderPhotoUrl,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isFirstInGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (msg.isFromMe) "You" else msg.senderName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (msg.isFromMe) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        msg.timestamp.toTimeString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            if (msg.replyToName != null) {
                Row(
                    modifier =
                        Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            msg.replyToName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (!msg.replyToPreview.isNullOrBlank()) {
                            Text(
                                msg.replyToPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            msg.imageUrls.forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Image",
                    modifier =
                        Modifier
                            .widthIn(max = 350.dp)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(url) },
                    contentScale = ContentScale.FillWidth,
                )
            }

            if (msg.content.isNotBlank()) {
                val rawHtml = msg.contentHtml.ifEmpty { msg.content }
                if (rawHtml.contains('<') && rawHtml.contains('>')) {
                    val cleanedHtml =
                        remember(rawHtml) {
                            com.squads.app.data.HtmlParser
                                .cleanForRendering(rawHtml)
                        }
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextColor(textColorArgb)
                                setLinkTextColor(linkColorArgb)
                                textSize = 15f
                                movementMethod = LinkMovementMethod.getInstance()
                            }
                        },
                        update = { textView ->
                            val spanned = Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_COMPACT)
                            textView.text =
                                spanned.toString().trimEnd().let { trimmed ->
                                    spanned.subSequence(0, trimmed.length)
                                }
                            textView.setTextColor(textColorArgb)
                            textView.setLinkTextColor(linkColorArgb)
                        },
                    )
                } else {
                    Text(
                        msg.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Reactions
            if (msg.reactions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    msg.reactions.forEach { reaction ->
                        ReactionChip(reaction.emoji, reaction.count)
                    }
                }
            }
        }
    }
}
