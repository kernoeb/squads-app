package com.squads.app.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.squads.app.data.Channel
import com.squads.app.data.ChannelMessage
import com.squads.app.data.Team
import com.squads.app.data.toRelativeTime
import com.squads.app.ui.components.Avatar
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.ReactionChip
import com.squads.app.viewmodel.TeamsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(viewModel: TeamsViewModel = hiltViewModel()) {
    val teams by viewModel.teams.collectAsState()
    val selectedTeam by viewModel.selectedTeam.collectAsState()
    val channelMessages by viewModel.channelMessages.collectAsState()
    val selectedChannelName by viewModel.selectedChannelName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading && teams.isEmpty() && selectedTeam == null) {
        LoadingScreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            selectedChannelName != null -> {
                // Channel messages view
                TopAppBar(
                    title = { Text("# $selectedChannelName") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                ChannelMessagesView(messages = channelMessages)
            }
            selectedTeam != null -> {
                // Channels list
                TopAppBar(
                    title = { Text(selectedTeam!!.displayName) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                ChannelsListView(
                    team = selectedTeam!!,
                    onChannelClick = { channel ->
                        viewModel.selectChannel(selectedTeam!!.id, channel.id, channel.displayName)
                    },
                )
            }
            else -> {
                // Teams list
                TeamsListView(teams = teams, onTeamClick = { viewModel.selectTeam(it) })
            }
        }
    }
}

@Composable
private fun TeamsListView(
    teams: List<Team>,
    onTeamClick: (Team) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Teams",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        items(teams, key = { it.id }) { team ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onTeamClick(team) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(name = team.displayName, size = 48.dp, isGroup = true)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            team.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${team.channels.size} channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ChannelsListView(
    team: Team,
    onChannelClick: (Channel) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(team.channels, key = { it.id }) { channel ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(channel) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(channel.displayName, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelMessagesView(messages: List<ChannelMessage>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        items(messages, key = { it.id }) { msg ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = msg.senderName, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(msg.senderName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            msg.timestamp.toRelativeTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(msg.content, style = MaterialTheme.typography.bodyMedium)

                    if (msg.reactions.isNotEmpty() || msg.replyCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                msg.reactions.forEach { r -> ReactionChip(r.emoji, r.count) }
                            }
                            if (msg.replyCount > 0) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.height(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("${msg.replyCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
