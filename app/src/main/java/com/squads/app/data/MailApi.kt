package com.squads.app.data

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailApi
    @Inject
    constructor(
        private val api: TeamsApiClient,
    ) {
        suspend fun getMail(limit: Int = 25): List<MailMessage> {
            if (api.isDemoMode) return api.mockRepository.getMail()
            val url = "https://graph.microsoft.com/v1.0/me/messages?\$top=$limit&\$orderby=receivedDateTime desc"
            val arr = JSONObject(api.graphGet(url)).optJSONArray("value") ?: return emptyList()
            return arr.objects().map(::parseMailMessage)
        }

        suspend fun getMailDetail(messageId: String): MailMessage {
            if (api.isDemoMode) return api.mockRepository.getMailDetail(messageId)
            return parseMailMessage(JSONObject(api.graphGet("https://graph.microsoft.com/v1.0/me/messages/$messageId")))
        }

        private fun parseMailMessage(m: JSONObject): MailMessage {
            val from = m.optJSONObject("from")?.optJSONObject("emailAddress")
            val toList =
                (m.optJSONArray("toRecipients") ?: JSONArray())
                    .objects()
                    .map { it.optJSONObject("emailAddress")?.optString("address", "") ?: "" }

            return MailMessage(
                id = m.optString("id"),
                subject = m.optString("subject", "(No subject)"),
                bodyPreview = m.optString("bodyPreview", ""),
                body = m.optJSONObject("body")?.optString("content", "") ?: "",
                fromName = from?.optString("name", "Unknown") ?: "Unknown",
                fromAddress = from?.optString("address", "") ?: "",
                toRecipients = toList,
                receivedDateTime = api.parseTimestamp(m.optString("receivedDateTime", "")),
                isRead = m.optBoolean("isRead", true),
                isDraft = m.optBoolean("isDraft", false),
                hasAttachments = m.optBoolean("hasAttachments", false),
                importance = m.optString("importance", "normal"),
            )
        }
    }
