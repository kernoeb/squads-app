package com.squads.app.ui.chats

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.squads.app.data.ChatMessage
import com.squads.app.data.toTimeString
import com.squads.app.ui.components.Avatar
import com.squads.app.ui.components.ReactionChip
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DateSeparator(date: LocalDate) {
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
fun SwipeToReply(
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val haptic = LocalHapticFeedback.current
    var pastThreshold by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        val p = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.5f + 0.5f * p
                        scaleY = 0.5f + 0.5f * p
                    },
            tint =
                if (pastThreshold) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (pastThreshold) onReply()
                                pastThreshold = false
                                scope.launch { offsetX.animateTo(0f) }
                            },
                            onDragCancel = {
                                pastThreshold = false
                                scope.launch { offsetX.animateTo(0f) }
                            },
                        ) { _, dragAmount ->
                            val maxDrag = -thresholdPx * 1.3f
                            val newValue =
                                (offsetX.value + dragAmount).coerceIn(maxDrag, 0f)
                            scope.launch { offsetX.snapTo(newValue) }
                            val nowPast = newValue <= -thresholdPx
                            if (nowPast && !pastThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            pastThreshold = nowPast
                        }
                    },
        ) {
            content()
        }
    }
}

@Composable
fun ReplyBanner(
    message: ChatMessage,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (message.isFromMe) "You" else message.senderName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                message.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Cancel reply",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
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
                        ReactionChip(reaction.emoji, reaction.count, imageUrl = reaction.imageUrl)
                    }
                }
            }
        }
    }
}
