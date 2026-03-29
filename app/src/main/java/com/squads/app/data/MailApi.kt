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
        suspend fun getMailFolders(): List<MailFolder> {
            if (api.isDemoMode) return api.mockRepository.getMailFolders()
            val url = "https://graph.microsoft.com/v1.0/me/mailFolders?\$top=25"
            val arr = JSONObject(api.graphGet(url)).optJSONArray("value") ?: return emptyList()
            return arr.objects().map { f ->
                MailFolder(
                    id = f.optString("id"),
                    displayName = f.optString("displayName"),
                    unreadItemCount = f.optInt("unreadItemCount", 0),
                )
            }
        }

        suspend fun getMail(
            limit: Int = 25,
            folderId: String? = null,
        ): List<MailMessage> {
            if (api.isDemoMode) return api.mockRepository.getMail(folderId)
            val base =
                if (folderId != null) {
                    "https://graph.microsoft.com/v1.0/me/mailFolders/$folderId/messages"
                } else {
                    "https://graph.microsoft.com/v1.0/me/messages"
                }
            val url = "$base?\$top=$limit&\$orderby=receivedDateTime desc"
            val arr = JSONObject(api.graphGet(url)).optJSONArray("value") ?: return emptyList()
            return arr.objects().map { parseMailMessage(it, folderId ?: "") }
        }

        suspend fun getMailDetail(messageId: String): MailMessage {
            if (api.isDemoMode) return api.mockRepository.getMailDetail(messageId)
            val json = JSONObject(api.graphGet("https://graph.microsoft.com/v1.0/me/messages/$messageId?\$expand=attachments"))
            val mail = parseMailMessage(json)
            val attachments = json.optJSONArray("attachments") ?: return mail
            val cidMap = mutableMapOf<String, String>()
            for (att in attachments.objects()) {
                if (!att.optBoolean("isInline", false)) continue
                val contentId = att.optString("contentId", "").ifEmpty { continue }
                val contentType = att.optString("contentType", "image/png")
                val contentBytes = att.optString("contentBytes", "").ifEmpty { continue }
                cidMap[contentId] = "data:$contentType;base64,$contentBytes"
            }
            if (cidMap.isEmpty()) return mail
            val body =
                CID_REGEX.replace(mail.body) { match ->
                    cidMap[match.groupValues[1]] ?: match.value
                }
            return mail.copy(body = body)
        }

        suspend fun getInboxFolderId(): String? {
            if (api.isDemoMode) return "inbox"
            return try {
                val json = JSONObject(api.graphGet("https://graph.microsoft.com/v1.0/me/mailFolders/inbox?\$select=id"))
                json.optString("id").ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

        suspend fun getGraphToken(): String? = api.getTokenForUrl("https://graph.microsoft.com")

        private fun parseMailMessage(
            m: JSONObject,
            folderId: String = "",
        ): MailMessage {
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
                receivedDateTime = parseTimestamp(m.optString("receivedDateTime", "")),
                isRead = m.optBoolean("isRead", true),
                isDraft = m.optBoolean("isDraft", false),
                hasAttachments = m.optBoolean("hasAttachments", false),
                importance = MailImportance.fromString(m.optString("importance", "normal")),
                folderId = m.optString("parentFolderId", folderId),
            )
        }

        companion object {
            private val CID_REGEX = Regex("""cid:([^"'\s)]+)""")
        }
    }
