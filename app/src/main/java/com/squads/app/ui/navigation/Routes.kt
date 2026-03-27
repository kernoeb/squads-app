package com.squads.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Top-level tab routes (back stack roots)
@Serializable
data object ChatsList : NavKey

@Serializable
data object MailList : NavKey

@Serializable
data object Calendar : NavKey

@Serializable
data object Teams : NavKey

@Serializable
data object Search : NavKey

// Detail routes (pushed onto current tab's stack)
@Serializable
data class ChatDetail(
    val chatId: String,
) : NavKey

@Serializable
data class MailDetail(
    val mailId: String,
) : NavKey

@Serializable
data class Profile(
    val myUserId: String? = null,
) : NavKey
