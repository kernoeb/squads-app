package com.squads.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squads.app.data.PresenceAvailability

@Composable
fun UnreadBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
fun PresenceBadge(
    availability: PresenceAvailability,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val color =
        when (availability) {
            PresenceAvailability.Available,
            PresenceAvailability.AvailableIdle,
            -> Color(0xFF92C353)
            PresenceAvailability.Busy,
            PresenceAvailability.BusyIdle,
            PresenceAvailability.DoNotDisturb,
            -> Color(0xFFC4314B)
            PresenceAvailability.Away,
            PresenceAvailability.BeRightBack,
            -> Color(0xFFFFC107)
            PresenceAvailability.Offline,
            PresenceAvailability.Unknown,
            -> Color(0xFF8A8886)
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size * 0.75f)
                    .clip(CircleShape)
                    .background(color),
        )
    }
}

@Composable
fun ReactionChip(
    emoji: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 1) "$emoji $count" else emoji,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun ImportanceBadge(
    importance: String,
    modifier: Modifier = Modifier,
) {
    if (importance != "high") return
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color(0xFFD13438))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("!", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
