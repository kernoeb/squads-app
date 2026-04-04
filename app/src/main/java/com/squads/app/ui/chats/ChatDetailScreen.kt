package com.squads.app.ui.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.squads.app.data.ChatMessage
import com.squads.app.data.graphProfilePhotoUrl
import com.squads.app.ui.components.ChatAvatar
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.viewmodel.ChatsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch
import java.time.Duration

private const val MESSAGE_GROUP_GAP_MINUTES = 5L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
) {
    val chat by viewModel.selectedChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val messagesLoading by viewModel.messagesLoading.collectAsState()
    val presenceMap by viewModel.presenceMap.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scope = rememberCoroutineScope()
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var lastReplyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    if (replyingTo != null) lastReplyTarget = replyingTo

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
                        val memberPresence =
                            if (currentChat.isOneOnOne) {
                                currentChat.memberId?.let { presenceMap[it] }
                            } else {
                                null
                            }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChatAvatar(
                                chat = currentChat,
                                size = 36.dp,
                                presence = memberPresence,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    currentChat.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val subtitle =
                                    if (currentChat.isOneOnOne) {
                                        memberPresence?.displayName ?: "Chat"
                                    } else {
                                        "${currentChat.memberCount} members"
                                    }
                                Text(
                                    subtitle,
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
                    .imePadding()
                    .hazeSource(state = hazeState),
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
                            val msgDate = msg.timestamp.toLocalDate()
                            val prevMsgDate = prevMsg?.timestamp?.toLocalDate()

                            val sameSender =
                                if (msg.isFromMe || prevMsg?.isFromMe == true) {
                                    msg.isFromMe == prevMsg?.isFromMe
                                } else {
                                    msg.senderId == prevMsg?.senderId
                                }
                            val tooFarApart =
                                prevMsg != null &&
                                    Duration.between(msg.timestamp, prevMsg.timestamp).abs().toMinutes() >= MESSAGE_GROUP_GAP_MINUTES
                            val isFirstInGroup =
                                prevMsg == null || !sameSender || prevMsgDate != msgDate || tooFarApart

                            item(key = msg.id, contentType = "message") {
                                SwipeToReply(onReply = { replyingTo = msg }) {
                                    MessageRow(
                                        msg = msg,
                                        senderPhotoUrl = graphProfilePhotoUrl(msg.senderObjectId),
                                        isFirstInGroup = isFirstInGroup,
                                        onImageClick = { url -> fullscreenImageUrl = url },
                                    )
                                }
                            }

                            if (prevMsgDate != msgDate) {
                                item(key = "date-$msgDate", contentType = "dateSeparator") {
                                    DateSeparator(msgDate)
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

            AnimatedVisibility(
                visible = replyingTo != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                lastReplyTarget?.let { msg ->
                    ReplyBanner(
                        message = msg,
                        onDismiss = { replyingTo = null },
                    )
                }
            }

            MessageInput(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(chat?.id ?: "", inputText, replyingTo)
                        inputText = ""
                        replyingTo = null
                    }
                },
                hazeState = hazeState,
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

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    hazeState: HazeState,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hazeEffect(state = hazeState, style = CupertinoMaterials.regular())
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
