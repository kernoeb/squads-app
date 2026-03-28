package com.squads.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.squads.app.data.ChatConversation
import com.squads.app.data.PresenceAvailability
import com.squads.app.data.graphProfilePhotoUrl

@Composable
fun ChatAvatar(
    chat: ChatConversation,
    size: Dp = 48.dp,
    presence: PresenceAvailability? = null,
) {
    if (!chat.isOneOnOne && chat.memberIds.size >= 2) {
        GroupAvatar(
            names = chat.memberNames,
            size = size,
            photoUrls = chat.memberIds.map { graphProfilePhotoUrl(it) },
        )
    } else {
        Avatar(
            name = chat.title,
            size = size,
            isGroup = !chat.isOneOnOne,
            photoUrl = chat.memberId?.let { graphProfilePhotoUrl(it) },
            presence = presence,
        )
    }
}
