package com.squads.app.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.squads.app.data.ChatConversation
import com.squads.app.data.graphProfilePhotoUrl
import com.squads.app.data.toRelativeTime
import com.squads.app.ui.components.Avatar
import com.squads.app.ui.components.ScreenHeader
import com.squads.app.ui.components.GroupAvatar
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.UnreadBadge
import com.squads.app.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel = hiltViewModel(),
    onChatClick: (ChatConversation) -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading && chats.isEmpty()) {
        LoadingScreen()
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            ScreenHeader("Chats") {
                IconButton(onClick = onProfileClick) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(chats, key = { it.id }, contentType = { "chat" }) { chat ->
            ChatRow(
                chat = chat,
                onClick = {
                    viewModel.selectChat(chat)
                    onChatClick(chat)
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ChatRow(
    chat: ChatConversation,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!chat.isOneOnOne && chat.memberIds.size >= 2) {
            GroupAvatar(
                names = chat.memberNames,
                photoUrls = chat.memberIds.map { graphProfilePhotoUrl(it) },
            )
        } else {
            Avatar(
                name = chat.title,
                isGroup = !chat.isOneOnOne,
                photoUrl = chat.memberId?.let { graphProfilePhotoUrl(it) },
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (chat.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = chat.lastMessageTime.toRelativeTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (chat.isUnread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.isUnread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge()
                }
            }
        }
    }
}
