package com.squads.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.squads.app.data.CalendarEvent
import com.squads.app.data.toTimeString
import com.squads.app.ui.components.EmptyScreen
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.ScreenHeader
import com.squads.app.ui.theme.BottomNavHeight
import com.squads.app.viewmodel.CalendarViewModel
import java.time.format.DateTimeFormatter

private val DAY_HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()
    val showWeek by viewModel.showWeek.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAppResumed()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Calendar") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showWeek,
                    onClick = { if (showWeek) viewModel.toggleWeekView() },
                    label = { Text("Today") },
                )
                FilterChip(
                    selected = showWeek,
                    onClick = { if (!showWeek) viewModel.toggleWeekView() },
                    label = { Text("Week") },
                )
            }
        }

        if (showWeek) {
            val weekStart by viewModel.weekStartDate.collectAsState()
            val weekLabel =
                remember(weekStart) {
                    val formatter = DateTimeFormatter.ofPattern("MMM d")
                    "${weekStart.format(formatter)} - ${weekStart.plusDays(6).format(formatter)}"
                }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                Text(
                    weekLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = { viewModel.nextWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
        }

        val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding = BottomNavHeight + systemNavInset

        if (isLoading && events.isEmpty()) {
            LoadingScreen(Modifier.weight(1f).padding(bottom = bottomPadding))
        } else if (events.isEmpty()) {
            EmptyScreen(
                title = if (showWeek) "No events this week" else "No events today",
                subtitle = "Your schedule is clear!",
                icon = Icons.Default.CalendarMonth,
                modifier = Modifier.weight(1f).padding(bottom = bottomPadding),
            )
        } else {
            val grouped = remember(events) { events.groupBy { it.startTime.toLocalDate() } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = BottomNavHeight + systemNavInset),
            ) {
                grouped.forEach { (date, dayEvents) ->
                    item(contentType = "dateHeader") {
                        Text(
                            text = date.format(DAY_HEADER_FORMATTER),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(dayEvents, key = { it.id }, contentType = { "event" }) { event ->
                        EventCard(event = event, onClick = { viewModel.selectEvent(event) })
                    }
                }
            }
        }
    }

    // Event detail bottom sheet
    if (selectedEvent != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissEvent() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EventDetailSheet(event = selectedEvent!!)
        }
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
) {
    val responseColor =
        when (event.responseStatus) {
            "accepted" -> Color(0xFF107C10)
            "tentative" -> Color(0xFFFFB900)
            "declined" -> Color(0xFFD13438)
            else -> MaterialTheme.colorScheme.outline
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Time column
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(52.dp),
        ) {
            Text(
                event.startTime.toTimeString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                event.endTime.toTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Color indicator
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(responseColor),
        )

        Spacer(Modifier.width(12.dp))

        // Event details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.subject,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.isOnlineMeeting) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.location != null) {
                    if (event.isOnlineMeeting) {
                        Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventDetailSheet(event: CalendarEvent) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            event.subject,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        DetailRow(Icons.Default.Schedule, "${event.startTime.toTimeString()} - ${event.endTime.toTimeString()}")
        if (event.location != null) DetailRow(Icons.Default.LocationOn, event.location)
        if (event.isOnlineMeeting) DetailRow(Icons.Default.Videocam, "Join online meeting")

        if (event.attendees.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Attendees", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            event.attendees.forEach { attendee ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (attendee.response) {
                                        "accepted" -> Color(0xFF107C10)
                                        "tentative" -> Color(0xFFFFB900)
                                        "declined" -> Color(0xFFD13438)
                                        else -> Color.Gray
                                    },
                                ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(attendee.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "(${attendee.response})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
