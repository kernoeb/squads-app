package com.squads.app.data

import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock data repository for demo mode.
 * Provides realistic sample data for all screens.
 */
@Singleton
class MockRepository
    @Inject
    constructor() {
        private val now = LocalDateTime.now()
        private val myId = "me-user-id"

        // ─── Current user ───────────────────────────────────────────

        fun getMe(): UserProfile =
            UserProfile(
                id = myId,
                displayName = "You",
                email = "you@contoso.com",
                jobTitle = "Software Engineer",
            )

        // ─── Presence ───────────────────────────────────────────────

        fun getPresences(userIds: List<String>): Map<String, String> =
            userIds.associateWith { id ->
                when (id.hashCode().mod(5)) {
                    0 -> "Available"
                    1 -> "Busy"
                    2 -> "Away"
                    3 -> "Available"
                    else -> "DoNotDisturb"
                }
            }

        // ─── Chats ──────────────────────────────────────────────────

        fun getChats(): List<ChatConversation> =
            listOf(
                ChatConversation(
                    "c1",
                    "Alice Martin",
                    "Hey, did you review the PR?",
                    now.minusMinutes(5),
                    isUnread = true,
                    memberId = "alice-id",
                ),
                ChatConversation("c2", "Bob Chen", "Sure, I'll send it over", now.minusMinutes(42), memberId = "bob-id"),
                ChatConversation("c3", "Design Team", "Updated the mockups 🎨", now.minusHours(2), isOneOnOne = false, memberCount = 6),
                ChatConversation("c4", "Clara Diaz", "Thanks for the help!", now.minusHours(4), memberId = "clara-id"),
                ChatConversation("c5", "Backend Guild", "Deploying v2.3 tonight", now.minusHours(8), isOneOnOne = false, memberCount = 12),
                ChatConversation("c6", "Dev Tanaka", "Can we sync tomorrow?", now.minusDays(1), memberId = "dev-id"),
                ChatConversation("c7", "Emma Wilson", "The meeting notes are attached", now.minusDays(1), memberId = "emma-id"),
                ChatConversation(
                    "c8",
                    "Sprint Planning",
                    "Next sprint starts Monday",
                    now.minusDays(2),
                    isOneOnOne = false,
                    memberCount = 8,
                ),
                ChatConversation("c9", "Frank Lopez", "Sounds good 👍", now.minusDays(3), memberId = "frank-id"),
                ChatConversation("c10", "Grace Kim", "Let me check and get back to you", now.minusDays(4), memberId = "grace-id"),
            )

        fun getMessages(chatId: String): List<ChatMessage> =
            when (chatId) {
                "c1" ->
                    listOf(
                        ChatMessage(
                            id = "m1",
                            content = "Hey! How's the new feature going?",
                            senderName = "Alice Martin",
                            senderId = "alice",
                            timestamp = now.minusHours(2),
                        ),
                        ChatMessage(
                            id = "m2",
                            content = "Pretty good, almost done with the API integration",
                            senderName = "You",
                            senderId = myId,
                            timestamp = now.minusHours(1),
                            isFromMe = true,
                        ),
                        ChatMessage(
                            id = "m3",
                            content = "Nice! Can you push it before EOD?",
                            senderName = "Alice Martin",
                            senderId = "alice",
                            timestamp = now.minusMinutes(45),
                        ),
                        ChatMessage(
                            id = "m4",
                            content = "Sure, just writing tests now",
                            senderName = "You",
                            senderId = myId,
                            timestamp = now.minusMinutes(30),
                            isFromMe = true,
                        ),
                        ChatMessage(
                            id = "m5",
                            content = "Hey, did you review the PR?",
                            senderName = "Alice Martin",
                            senderId = "alice",
                            timestamp = now.minusMinutes(5),
                            reactions = listOf(Reaction("👀", 1)),
                        ),
                    )
                "c3" ->
                    listOf(
                        ChatMessage(
                            id = "m10",
                            content = "Hey team, I updated the design mockups",
                            senderName = "Sarah Designer",
                            senderId = "sarah",
                            timestamp = now.minusHours(3),
                        ),
                        ChatMessage(
                            id = "m11",
                            content = "Looking great! Love the new color scheme",
                            senderName = "You",
                            senderId = myId,
                            timestamp = now.minusHours(2),
                            isFromMe = true,
                        ),
                        ChatMessage(
                            id = "m12",
                            content = "The spacing on the cards needs adjustment though",
                            senderName = "Mike PM",
                            senderId = "mike",
                            timestamp = now.minusHours(2),
                        ),
                        ChatMessage(
                            id = "m13",
                            content = "Updated the mockups 🎨",
                            senderName = "Sarah Designer",
                            senderId = "sarah",
                            timestamp = now.minusHours(2),
                            reactions = listOf(Reaction("🎨", 3), Reaction("👍", 2)),
                        ),
                    )
                else ->
                    listOf(
                        ChatMessage(
                            id = "m20",
                            content = "Hi there!",
                            senderName = "Contact",
                            senderId = "other",
                            timestamp = now.minusHours(1),
                        ),
                        ChatMessage(
                            id = "m21",
                            content = "Hey! What's up?",
                            senderName = "You",
                            senderId = myId,
                            timestamp = now.minusMinutes(30),
                            isFromMe = true,
                        ),
                    )
            }

        // ─── Mail ───────────────────────────────────────────────────

        fun getMail(): List<MailMessage> =
            listOf(
                MailMessage(
                    "e1",
                    "Q1 Review — Action items",
                    "Please find attached the action items from today's quarterly review. We need to address the performance metrics by next Friday.",
                    "",
                    "Patricia VP",
                    "patricia@contoso.com",
                    receivedDateTime = now.minusHours(1),
                    isRead = false,
                    hasAttachments = true,
                    importance = "high",
                ),
                MailMessage(
                    "e2",
                    "Re: API Design Proposal",
                    "Looks good to me. Let's schedule a follow-up to discuss the authentication flow in more detail.",
                    "",
                    "James Architect",
                    "james@contoso.com",
                    receivedDateTime = now.minusHours(3),
                ),
                MailMessage(
                    "e3",
                    "Team lunch Friday 🍕",
                    "Hey everyone! Let's grab lunch together this Friday. I'm thinking pizza. Any dietary restrictions?",
                    "",
                    "Rachel Social",
                    "rachel@contoso.com",
                    receivedDateTime = now.minusHours(6),
                ),
                MailMessage(
                    "e4",
                    "Build pipeline update",
                    "The CI/CD pipeline has been updated. Please review the new configuration and let me know if you notice any issues.",
                    "",
                    "DevOps Bot",
                    "devops@contoso.com",
                    receivedDateTime = now.minusDays(1),
                ),
                MailMessage(
                    "e5",
                    "Re: Office relocation",
                    "The new office will be ready by March 15th. Parking details will follow in a separate email.",
                    "",
                    "Facilities",
                    "facilities@contoso.com",
                    receivedDateTime = now.minusDays(1),
                ),
                MailMessage(
                    "e6",
                    "Your expense report has been approved",
                    "Your expense report #4521 for \$342.50 has been approved and will be reimbursed in the next pay cycle.",
                    "",
                    "Finance System",
                    "finance@contoso.com",
                    receivedDateTime = now.minusDays(2),
                ),
                MailMessage(
                    "e7",
                    "Invitation: Architecture Review",
                    "You're invited to the architecture review on Thursday at 2 PM.",
                    "",
                    "Calendar",
                    "noreply@contoso.com",
                    receivedDateTime = now.minusDays(2),
                ),
                MailMessage(
                    "e8",
                    "Welcome to the new analytics dashboard",
                    "We're excited to announce our new analytics dashboard. Check it out at analytics.contoso.com",
                    "",
                    "Product Team",
                    "product@contoso.com",
                    receivedDateTime = now.minusDays(3),
                ),
            )

        fun getMailDetail(messageId: String): MailMessage {
            val mail = getMail().first { it.id == messageId }
            return mail.copy(
                body =
                    """
                    <p>Hi team,</p>
                    <p>${mail.bodyPreview}</p>
                    <p>Best regards,<br/>${mail.fromName}</p>
                    """.trimIndent(),
            )
        }

        // ─── Calendar ───────────────────────────────────────────────

        fun getTodayEvents(): List<CalendarEvent> =
            listOf(
                CalendarEvent(
                    "ev1",
                    "Daily Standup",
                    now.withHour(9).withMinute(30),
                    now.withHour(9).withMinute(45),
                    organizerName = "Scrum Master",
                    isOnlineMeeting = true,
                    meetingUrl = "https://teams.microsoft.com/meet/123",
                    responseStatus = "accepted",
                    attendees =
                        listOf(
                            EventAttendee("Alice", "alice@contoso.com", "accepted"),
                            EventAttendee("Bob", "bob@contoso.com", "accepted"),
                            EventAttendee("Clara", "clara@contoso.com", "tentative"),
                        ),
                ),
                CalendarEvent(
                    "ev2",
                    "1:1 with Manager",
                    now.withHour(11).withMinute(0),
                    now.withHour(11).withMinute(30),
                    organizerName = "Patricia VP",
                    isOnlineMeeting = true,
                    responseStatus = "accepted",
                ),
                CalendarEvent(
                    "ev3",
                    "Lunch Break",
                    now.withHour(12).withMinute(0),
                    now.withHour(13).withMinute(0),
                    location = "Cafeteria",
                    organizerName = "You",
                    isAllDay = false,
                    responseStatus = "accepted",
                ),
                CalendarEvent(
                    "ev4",
                    "Architecture Review",
                    now.withHour(14).withMinute(0),
                    now.withHour(15).withMinute(0),
                    location = "Room 4B",
                    organizerName = "James Architect",
                    isOnlineMeeting = true,
                    responseStatus = "tentative",
                    attendees =
                        listOf(
                            EventAttendee("James", "james@contoso.com", "accepted"),
                            EventAttendee("You", "you@contoso.com", "tentative"),
                            EventAttendee("Dev", "dev@contoso.com", "none"),
                        ),
                ),
                CalendarEvent(
                    "ev5",
                    "Sprint Retro",
                    now.withHour(16).withMinute(0),
                    now.withHour(16).withMinute(45),
                    organizerName = "Scrum Master",
                    isOnlineMeeting = true,
                    responseStatus = "accepted",
                ),
            )

        fun getWeekEvents(): List<CalendarEvent> {
            val today = getTodayEvents()
            val tomorrow =
                listOf(
                    CalendarEvent(
                        "ev6",
                        "Design Review",
                        now.plusDays(1).withHour(10).withMinute(0),
                        now.plusDays(1).withHour(11).withMinute(0),
                        organizerName = "Sarah Designer",
                        isOnlineMeeting = true,
                        responseStatus = "accepted",
                    ),
                    CalendarEvent(
                        "ev7",
                        "Team Lunch 🍕",
                        now.plusDays(1).withHour(12).withMinute(0),
                        now.plusDays(1).withHour(13).withMinute(0),
                        location = "Pizza Place",
                        organizerName = "Rachel Social",
                        responseStatus = "accepted",
                    ),
                )
            val dayAfter =
                listOf(
                    CalendarEvent(
                        "ev8",
                        "Release Planning",
                        now.plusDays(2).withHour(9).withMinute(0),
                        now.plusDays(2).withHour(10).withMinute(30),
                        organizerName = "PM Lead",
                        isOnlineMeeting = true,
                        responseStatus = "accepted",
                    ),
                    CalendarEvent(
                        "ev9",
                        "Candidate Interview",
                        now.plusDays(2).withHour(14).withMinute(0),
                        now.plusDays(2).withHour(15).withMinute(0),
                        location = "Room 2A",
                        organizerName = "HR",
                        responseStatus = "accepted",
                    ),
                )
            return today + tomorrow + dayAfter
        }

        // ─── Teams ──────────────────────────────────────────────────

        fun getTeams(): List<Team> =
            listOf(
                Team(
                    "t1",
                    "Engineering",
                    listOf(
                        Channel("ch1", "General"),
                        Channel("ch2", "Backend"),
                        Channel("ch3", "Frontend"),
                        Channel("ch4", "DevOps"),
                    ),
                ),
                Team(
                    "t2",
                    "Product",
                    listOf(
                        Channel("ch5", "General"),
                        Channel("ch6", "Roadmap"),
                        Channel("ch7", "Customer Feedback"),
                    ),
                ),
                Team(
                    "t3",
                    "Design",
                    listOf(
                        Channel("ch8", "General"),
                        Channel("ch9", "Brand"),
                        Channel("ch10", "UX Research"),
                    ),
                ),
                Team(
                    "t4",
                    "All Company",
                    listOf(
                        Channel("ch11", "General"),
                        Channel("ch12", "Announcements"),
                        Channel("ch13", "Random"),
                    ),
                ),
            )

        fun getChannelMessages(
            teamId: String,
            channelId: String,
        ): List<ChannelMessage> =
            listOf(
                ChannelMessage(
                    "tm1",
                    "Deployed the new auth service to staging. Please test when you get a chance.",
                    "Dev Tanaka",
                    now.minusHours(1),
                    replyCount = 3,
                    reactions = listOf(Reaction("🚀", 4)),
                ),
                ChannelMessage(
                    "tm2",
                    "FYI: the database migration will run tonight at 11 PM UTC. Expect ~5 min of downtime.",
                    "DBA Admin",
                    now.minusHours(3),
                    replyCount = 1,
                    reactions = listOf(Reaction("👍", 6)),
                ),
                ChannelMessage(
                    "tm3",
                    "PR #482 is ready for review — adds the new caching layer",
                    "Alice Martin",
                    now.minusHours(5),
                    replyCount = 7,
                    reactions = listOf(Reaction("👀", 2)),
                ),
                ChannelMessage(
                    "tm4",
                    "Reminder: code freeze starts tomorrow at 5 PM",
                    "Release Manager",
                    now.minusDays(1),
                    replyCount = 0,
                    reactions = listOf(Reaction("✅", 8)),
                ),
            )

        // ─── Search ─────────────────────────────────────────────────

        fun search(query: String): List<SearchResult> {
            if (query.isBlank()) return emptyList()
            val q = query.lowercase()
            val results = mutableListOf<SearchResult>()

            getChats()
                .filter { it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q) }
                .forEach { results.add(SearchResult(SearchResultType.CHAT, it.title, it.lastMessage, it.lastMessage, it.id)) }

            getMail()
                .filter { it.subject.lowercase().contains(q) || it.bodyPreview.lowercase().contains(q) }
                .forEach { results.add(SearchResult(SearchResultType.MAIL, it.subject, it.fromName, it.bodyPreview, it.id)) }

            getTodayEvents()
                .filter { it.subject.lowercase().contains(q) }
                .forEach {
                    results.add(
                        SearchResult(SearchResultType.CALENDAR, it.subject, it.organizerName, it.location ?: "Online", it.id),
                    )
                }

            return results
        }
    }
