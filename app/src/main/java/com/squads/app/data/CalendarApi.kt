package com.squads.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarApi
    @Inject
    constructor(
        private val api: TeamsApiClient,
    ) {
        suspend fun getEvents(days: Int): List<CalendarEvent> {
            if (api.isDemoMode) return if (days <= 1) api.mockRepository.getTodayEvents() else api.mockRepository.getWeekEvents()
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            val start = now.toLocalDate().atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
            val end =
                now
                    .toLocalDate()
                    .plusDays(days.toLong())
                    .atStartOfDay(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ISO_INSTANT)

            val url =
                "https://graph.microsoft.com/v1.0/me/calendarView" +
                    "?startDateTime=$start&endDateTime=$end&\$orderby=start/dateTime&\$top=50"
            val arr = JSONObject(api.graphGet(url)).optJSONArray("value") ?: return emptyList()

            return arr.objects().map { e ->
                val organizer = e.optJSONObject("organizer")?.optJSONObject("emailAddress")
                val attendees =
                    (e.optJSONArray("attendees") ?: JSONArray()).objects().map { a ->
                        val email = a.optJSONObject("emailAddress")
                        EventAttendee(
                            name = email?.optString("name", "") ?: "",
                            email = email?.optString("address", "") ?: "",
                            response = a.optJSONObject("status")?.optString("response", "none") ?: "none",
                        )
                    }

                CalendarEvent(
                    id = e.optString("id"),
                    subject = e.optString("subject", "(No subject)"),
                    startTime = parseTimestamp(e.optJSONObject("start")?.optString("dateTime", "") ?: ""),
                    endTime = parseTimestamp(e.optJSONObject("end")?.optString("dateTime", "") ?: ""),
                    location = e.optJSONObject("location")?.optString("displayName", "")?.ifEmpty { null },
                    organizerName = organizer?.optString("name", "Unknown") ?: "Unknown",
                    isOnlineMeeting = e.optBoolean("isOnlineMeeting", false),
                    meetingUrl = e.optJSONObject("onlineMeeting")?.optString("joinUrl", ""),
                    isAllDay = e.optBoolean("isAllDay", false),
                    isCancelled = e.optBoolean("isCancelled", false),
                    responseStatus = e.optJSONObject("responseStatus")?.optString("response", "none") ?: "none",
                    attendees = attendees,
                )
            }
        }
    }
