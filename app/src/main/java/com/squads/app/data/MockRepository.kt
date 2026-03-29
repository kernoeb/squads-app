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

        fun getPresences(userIds: List<String>): Map<String, PresenceAvailability> =
            userIds.associateWith { id ->
                when (id.hashCode().mod(5)) {
                    0 -> PresenceAvailability.Available
                    1 -> PresenceAvailability.Busy
                    2 -> PresenceAvailability.Away
                    3 -> PresenceAvailability.Available
                    else -> PresenceAvailability.DoNotDisturb
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
                ChatConversation("c11", "Hannah Park", "I'll review it tonight", now.minusDays(5), memberId = "hannah-id"),
                ChatConversation("c12", "Platform Team", "CI is green again 🟢", now.minusDays(5), isOneOnOne = false, memberCount = 15),
                ChatConversation("c13", "Ivan Petrov", "See you at the conference!", now.minusDays(6), memberId = "ivan-id"),
                ChatConversation("c14", "Julia Santos", "The docs are updated", now.minusDays(7), memberId = "julia-id"),
                ChatConversation("c15", "Kevin O'Brien", "Merged, thanks for the review", now.minusDays(8), memberId = "kevin-id"),
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

        fun getMailFolders(): List<MailFolder> =
            listOf(
                MailFolder("inbox", "Inbox", unreadItemCount = 2),
                MailFolder("sentitems", "Sent Items"),
                MailFolder("drafts", "Drafts"),
                MailFolder("deleteditems", "Deleted Items"),
                MailFolder("junkemail", "Junk Email"),
            )

        private val allMail: List<MailMessage> =
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
                    folderId = "inbox",
                ),
                MailMessage(
                    "e2",
                    "Re: API Design Proposal",
                    "Looks good to me. Let's schedule a follow-up to discuss the authentication flow in more detail.",
                    "",
                    "James Architect",
                    "james@contoso.com",
                    receivedDateTime = now.minusHours(3),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e3",
                    "Team lunch Friday 🍕",
                    "Hey everyone! Let's grab lunch together this Friday. I'm thinking pizza. Any dietary restrictions?",
                    "",
                    "Rachel Social",
                    "rachel@contoso.com",
                    receivedDateTime = now.minusHours(6),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e4",
                    "Build pipeline update",
                    "The CI/CD pipeline has been updated. Please review the new configuration and let me know if you notice any issues.",
                    "",
                    "DevOps Bot",
                    "devops@contoso.com",
                    receivedDateTime = now.minusDays(1),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e5",
                    "Re: Office relocation",
                    "The new office will be ready by March 15th. Parking details will follow in a separate email.",
                    "",
                    "Facilities",
                    "facilities@contoso.com",
                    receivedDateTime = now.minusDays(1),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e6",
                    "Your expense report has been approved",
                    "Your expense report #4521 for \$342.50 has been approved and will be reimbursed in the next pay cycle.",
                    "",
                    "Finance System",
                    "finance@contoso.com",
                    receivedDateTime = now.minusDays(2),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e7",
                    "Invitation: Architecture Review",
                    "You're invited to the architecture review on Thursday at 2 PM.",
                    "",
                    "Calendar",
                    "noreply@contoso.com",
                    receivedDateTime = now.minusDays(2),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e8",
                    "Welcome to the new analytics dashboard",
                    "We're excited to announce our new analytics dashboard. Check it out at analytics.contoso.com",
                    "",
                    "Product Team",
                    "product@contoso.com",
                    receivedDateTime = now.minusDays(3),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e9",
                    "Security training reminder",
                    "Annual security awareness training is due by end of month. Please complete the modules.",
                    "",
                    "IT Security",
                    "security@contoso.com",
                    receivedDateTime = now.minusDays(4),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e10",
                    "Re: Database migration plan",
                    "The migration window is confirmed for Saturday 2 AM - 4 AM UTC. Rollback plan attached.",
                    "",
                    "DBA Team",
                    "dba@contoso.com",
                    receivedDateTime = now.minusDays(4),
                    hasAttachments = true,
                    folderId = "inbox",
                ),
                MailMessage(
                    "e11",
                    "New hire onboarding — mentors needed",
                    "We have 3 new engineers joining next week. Please sign up as a mentor if available.",
                    "",
                    "HR Team",
                    "hr@contoso.com",
                    receivedDateTime = now.minusDays(5),
                    folderId = "inbox",
                ),
                MailMessage(
                    "e12",
                    "Hackathon results 🏆",
                    "Congratulations to all participants! See the winning projects and demos inside.",
                    "",
                    "Engineering Lead",
                    "eng-lead@contoso.com",
                    receivedDateTime = now.minusDays(6),
                    folderId = "inbox",
                ),
                // Sent items
                MailMessage(
                    "e13",
                    "Re: Q1 Review — Action items",
                    "Thanks Patricia, I'll update the tracker by end of day.",
                    "",
                    "You",
                    "you@contoso.com",
                    receivedDateTime = now.minusMinutes(30),
                    folderId = "sentitems",
                ),
                MailMessage(
                    "e14",
                    "Design review feedback",
                    "Hey Sarah, here's my feedback on the new mockups. Overall looks great!",
                    "",
                    "You",
                    "you@contoso.com",
                    receivedDateTime = now.minusDays(2),
                    folderId = "sentitems",
                ),
                // Drafts
                MailMessage(
                    "e15",
                    "Proposal: New CI/CD pipeline",
                    "I've been looking into replacing our current pipeline with...",
                    "",
                    "You",
                    "you@contoso.com",
                    receivedDateTime = now.minusHours(2),
                    isDraft = true,
                    folderId = "drafts",
                ),
                // Junk
                MailMessage(
                    "e16",
                    "You've won a free cruise!",
                    "Congratulations! You've been selected for an exclusive offer...",
                    "",
                    "Promo Deals",
                    "noreply@totallylegit.com",
                    receivedDateTime = now.minusDays(1),
                    folderId = "junkemail",
                ),
            )

        fun getMail(folderId: String? = null): List<MailMessage> =
            if (folderId != null) {
                allMail.filter { it.folderId == folderId }
            } else {
                allMail
            }

        fun getMailDetail(messageId: String): MailMessage {
            val mail = allMail.first { it.id == messageId }
            val body = MAIL_BODIES[messageId] ?: defaultMailBody(mail)
            return mail.copy(body = body)
        }

        private fun defaultMailBody(mail: MailMessage): String =
            """
            <p>Hi team,</p>
            <p>${mail.bodyPreview}</p>
            <p>Best regards,<br/>${mail.fromName}</p>
            """.trimIndent()

        @Suppress("ktlint:standard:max-line-length")
        private val MAIL_BODIES: Map<String, String> =
            mapOf(
                // e1: Q1 Review — rich HTML with table, inline styles, colored text
                "e1" to
                    """
                    <div style="font-family: Calibri, sans-serif; font-size: 14px;">
                    <p>Hi team,</p>
                    <p>Please find below the action items from today's <b>Q1 quarterly review</b>. We need to address the performance metrics by <span style="color: red; font-weight: bold;">next Friday</span>.</p>
                    <table border="1" cellpadding="8" cellspacing="0" style="border-collapse: collapse; width: 100%; margin: 16px 0;">
                        <tr style="background-color: #4472C4; color: white;">
                            <th>Action Item</th><th>Owner</th><th>Due Date</th><th>Status</th>
                        </tr>
                        <tr>
                            <td>Finalize Q2 roadmap</td><td>James Architect</td><td>Mar 28</td>
                            <td style="color: orange;">⏳ In Progress</td>
                        </tr>
                        <tr style="background-color: #f2f2f2;">
                            <td>Fix login latency regression</td><td>DevOps Bot</td><td>Mar 25</td>
                            <td style="color: green;">✅ Done</td>
                        </tr>
                        <tr>
                            <td>Update security training</td><td>IT Security</td><td>Mar 31</td>
                            <td style="color: red;">❌ Blocked</td>
                        </tr>
                    </table>
                    <p>Please update your items in the tracker. Let me know if you have any questions.</p>
                    <p>Best,<br/><b>Patricia VP</b><br/><span style="color: gray; font-size: 12px;">Vice President, Engineering · Contoso Ltd.</span></p>
                    </div>
                    """.trimIndent(),
                // e6: Expense report — automated system email with styled card
                "e6" to
                    """
                    <div style="font-family: Segoe UI, sans-serif; font-size: 14px; color: #333;">
                    <div style="background: #f0f9f0; border-left: 4px solid #28a745; padding: 16px; margin: 16px 0; border-radius: 4px;">
                        <p style="margin: 0 0 8px; font-size: 18px; font-weight: bold; color: #28a745;">✅ Expense Report Approved</p>
                        <table style="width: 100%; font-size: 14px;">
                            <tr><td style="padding: 4px 0; color: #666;">Report #</td><td style="padding: 4px 0;"><b>4521</b></td></tr>
                            <tr><td style="padding: 4px 0; color: #666;">Amount</td><td style="padding: 4px 0;"><b style="font-size: 16px;">$342.50</b></td></tr>
                            <tr><td style="padding: 4px 0; color: #666;">Category</td><td style="padding: 4px 0;">Travel &amp; Meals</td></tr>
                            <tr><td style="padding: 4px 0; color: #666;">Reimbursement</td><td style="padding: 4px 0;">Next pay cycle (Apr 1)</td></tr>
                        </table>
                    </div>
                    <p style="font-size: 12px; color: #999;">This is an automated message from the Contoso Finance System. Do not reply.</p>
                    </div>
                    """.trimIndent(),
                // e8: Product announcement — newsletter style with buttons and sections
                "e8" to
                    """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 32px; text-align: center; border-radius: 8px 8px 0 0;">
                        <h1 style="margin: 0; font-size: 24px;">🚀 New Analytics Dashboard</h1>
                        <p style="margin: 8px 0 0; opacity: 0.9;">Introducing real-time insights for your team</p>
                    </div>
                    <div style="padding: 24px; background: #ffffff; border: 1px solid #e0e0e0;">
                        <p>Hi team,</p>
                        <p>We're excited to announce our new analytics dashboard with the following features:</p>
                        <ul>
                            <li><b>Real-time metrics</b> — see your KPIs update live</li>
                            <li><b>Custom dashboards</b> — build views tailored to your team</li>
                            <li><b>Export to PDF</b> — share reports with stakeholders</li>
                            <li><b>Slack integration</b> — get alerts where you work</li>
                        </ul>
                        <div style="text-align: center; margin: 24px 0;">
                            <a href="https://analytics.contoso.com" style="background: #667eea; color: white; padding: 12px 32px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Try it now →</a>
                        </div>
                        <p style="color: #666; font-size: 13px;">Questions? Reach out to <a href="mailto:product@contoso.com">product@contoso.com</a></p>
                    </div>
                    <div style="padding: 16px; text-align: center; font-size: 11px; color: #999;">
                        Contoso Product Team · <a href="#" style="color: #999;">Unsubscribe</a>
                    </div>
                    </div>
                    """.trimIndent(),
                // e12: Hackathon results — rich with emojis, rankings, colored badges
                "e12" to
                    """
                    <div style="font-family: Segoe UI, sans-serif; font-size: 14px;">
                    <h2 style="color: #333;">🏆 Hackathon 2026 Results</h2>
                    <p>Congratulations to all 47 participants across 12 teams! Here are the winning projects:</p>
                    <div style="background: #fff8e1; border: 1px solid #ffcc02; border-radius: 8px; padding: 16px; margin: 12px 0;">
                        <p style="margin: 0 0 4px;"><span style="background: #FFD700; color: #333; padding: 2px 8px; border-radius: 4px; font-weight: bold;">🥇 1st Place</span></p>
                        <p style="margin: 4px 0; font-size: 16px;"><b>AI Code Reviewer</b> — Team Alpha</p>
                        <p style="margin: 4px 0; color: #666;">Automated PR reviews using LLM with codebase context</p>
                    </div>
                    <div style="background: #f5f5f5; border: 1px solid #ddd; border-radius: 8px; padding: 16px; margin: 12px 0;">
                        <p style="margin: 0 0 4px;"><span style="background: #C0C0C0; padding: 2px 8px; border-radius: 4px; font-weight: bold;">🥈 2nd Place</span></p>
                        <p style="margin: 4px 0; font-size: 16px;"><b>Smart Meeting Notes</b> — Team Beta</p>
                        <p style="margin: 4px 0; color: #666;">Real-time transcription and action item extraction</p>
                    </div>
                    <div style="background: #fff5ee; border: 1px solid #deb887; border-radius: 8px; padding: 16px; margin: 12px 0;">
                        <p style="margin: 0 0 4px;"><span style="background: #CD7F32; color: white; padding: 2px 8px; border-radius: 4px; font-weight: bold;">🥉 3rd Place</span></p>
                        <p style="margin: 4px 0; font-size: 16px;"><b>Zero-Click Deploy</b> — Team Gamma</p>
                        <p style="margin: 4px 0; color: #666;">One-button production deploys with automatic rollback</p>
                    </div>
                    <p>Demo recordings will be shared next week. Thanks to everyone who made this possible! 🎉</p>
                    <p>— Engineering Lead</p>
                    </div>
                    """.trimIndent(),
            )

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
                    id = "tm1",
                    subject = "Auth service v2 staging deploy",
                    content = "Deployed the new auth service to staging. Please test when you get a chance.",
                    senderName = "Dev Tanaka",
                    timestamp = now.minusHours(1),
                    reactions = listOf(Reaction("🚀", 4)),
                    replies =
                        listOf(
                            ChannelMessage(
                                id = "tm1r1",
                                content = "Testing now — login flow looks good so far",
                                senderName = "Alice Martin",
                                timestamp = now.minusMinutes(45),
                            ),
                            ChannelMessage(
                                id = "tm1r2",
                                content = "Found a 401 on the refresh token endpoint, can you check?",
                                senderName = "Bob Chen",
                                timestamp = now.minusMinutes(30),
                            ),
                            ChannelMessage(
                                id = "tm1r3",
                                content = "Fixed — was a misconfigured scope. Redeployed.",
                                senderName = "Dev Tanaka",
                                timestamp = now.minusMinutes(15),
                            ),
                        ),
                ),
                ChannelMessage(
                    id = "tm2",
                    subject = "Database migration tonight",
                    content = "FYI: the database migration will run tonight at 11 PM UTC. Expect ~5 min of downtime.",
                    senderName = "DBA Admin",
                    timestamp = now.minusHours(3),
                    reactions = listOf(Reaction("👍", 6)),
                    replies =
                        listOf(
                            ChannelMessage(
                                id = "tm2r1",
                                content = "Got it, thanks for the heads up!",
                                senderName = "Release Manager",
                                timestamp = now.minusHours(2),
                            ),
                        ),
                ),
                ChannelMessage(
                    id = "tm3",
                    subject = "PR #482 — caching layer",
                    content = "PR #482 is ready for review — adds the new caching layer",
                    senderName = "Alice Martin",
                    timestamp = now.minusHours(5),
                    reactions = listOf(Reaction("👀", 2)),
                    replies =
                        listOf(
                            ChannelMessage(
                                id = "tm3r1",
                                content = "Left a few comments on the eviction policy",
                                senderName = "Dev Tanaka",
                                timestamp = now.minusHours(4),
                            ),
                            ChannelMessage(
                                id = "tm3r2",
                                content = "Updated — switched to LRU with a 5-min TTL",
                                senderName = "Alice Martin",
                                timestamp = now.minusHours(3),
                                reactions = listOf(Reaction("👍", 2)),
                            ),
                        ),
                ),
                ChannelMessage(
                    id = "tm4",
                    content = "Reminder: code freeze starts tomorrow at 5 PM",
                    senderName = "Release Manager",
                    timestamp = now.minusDays(1),
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

            allMail
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
