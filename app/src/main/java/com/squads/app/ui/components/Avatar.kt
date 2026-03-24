package com.squads.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val avatarColors =
    listOf(
        Color(0xFF6264A7), // Teams purple
        Color(0xFF0078D4), // Microsoft blue
        Color(0xFF00A4EF), // Lighter blue
        Color(0xFF7FBA00), // Green
        Color(0xFFFFB900), // Yellow
        Color(0xFFF25022), // Red
        Color(0xFF8661C5), // Purple
        Color(0xFF00B7C3), // Teal
    )

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isGroup: Boolean = false,
    photo: ImageBitmap? = null,
) {
    val shape = if (isGroup) MaterialTheme.shapes.medium else CircleShape

    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier =
                modifier
                    .size(size)
                    .clip(shape),
        )
    } else {
        val initials =
            name
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifEmpty { "?" }

        val color = avatarColors[name.hashCode().mod(avatarColors.size)]

        Box(
            modifier =
                modifier
                    .size(size)
                    .clip(shape)
                    .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun GroupAvatar(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    photos: List<ImageBitmap?> = emptyList(),
) {
    val shape = MaterialTheme.shapes.medium
    val halfWidth = size / 2

    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape),
    ) {
        Row {
            AvatarHalf(photos.getOrNull(0), names.getOrElse(0) { "?" }, size, halfWidth, Alignment.CenterStart)
            AvatarHalf(photos.getOrNull(1), names.getOrElse(1) { "?" }, size, halfWidth, Alignment.CenterEnd)
        }
    }
}

@Composable
private fun AvatarHalf(
    photo: ImageBitmap?,
    name: String,
    size: Dp,
    width: Dp,
    alignment: Alignment,
) {
    Box(modifier = Modifier.width(width).size(size)) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                alignment = alignment,
            )
        } else {
            val initials = name.take(1).uppercase()
            val color = avatarColors[name.hashCode().mod(avatarColors.size)]
            Box(
                modifier = Modifier.size(size).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.32f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
