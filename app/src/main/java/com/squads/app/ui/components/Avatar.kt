package com.squads.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

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
    photoUrl: String? = null,
    presence: String? = null,
) {
    val shape = if (isGroup) MaterialTheme.shapes.medium else CircleShape

    if (presence != null) {
        Box(modifier = modifier) {
            AvatarContent(photoUrl, name, size, shape)
            PresenceBadge(
                availability = presence,
                modifier = Modifier.align(Alignment.BottomEnd),
                size = (size.value * 0.3f).dp,
            )
        }
    } else {
        AvatarContent(photoUrl, name, size, shape, modifier)
    }
}

@Composable
private fun AvatarContent(
    photoUrl: String?,
    name: String,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    if (photoUrl != null) {
        SubcomposeAsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier =
                modifier
                    .size(size)
                    .clip(shape),
            loading = { InitialsAvatar(name, size, shape) },
            error = { InitialsAvatar(name, size, shape) },
        )
    } else {
        InitialsAvatar(name, size, shape, modifier)
    }
}

@Composable
fun InitialsAvatar(
    name: String,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    val initials =
        remember(name) {
            name
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifEmpty { "?" }
        }

    val color = remember(name) { avatarColors[name.hashCode().mod(avatarColors.size)] }

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

@Composable
fun GroupAvatar(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    photoUrls: List<String?> = emptyList(),
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
            AvatarHalf(photoUrls.getOrNull(0), names.getOrElse(0) { "?" }, size, halfWidth, Alignment.CenterStart)
            AvatarHalf(photoUrls.getOrNull(1), names.getOrElse(1) { "?" }, size, halfWidth, Alignment.CenterEnd)
        }
    }
}

@Composable
private fun AvatarHalf(
    photoUrl: String?,
    name: String,
    size: Dp,
    width: Dp,
    alignment: Alignment,
) {
    Box(modifier = Modifier.width(width).size(size)) {
        if (photoUrl != null) {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                alignment = alignment,
                loading = { AvatarHalfFallback(name, size) },
                error = { AvatarHalfFallback(name, size) },
            )
        } else {
            AvatarHalfFallback(name, size)
        }
    }
}

@Composable
private fun AvatarHalfFallback(
    name: String,
    size: Dp,
) {
    val initials = remember(name) { name.take(1).uppercase() }
    val color = remember(name) { avatarColors[name.hashCode().mod(avatarColors.size)] }
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
