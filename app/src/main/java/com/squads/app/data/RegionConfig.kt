package com.squads.app.data

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegionConfig
    @Inject
    constructor(
        context: Context,
    ) {
        private val prefs = context.getSharedPreferences("squads_region", Context.MODE_PRIVATE)

        @Volatile
        var region: String? = prefs.getString("region", null)
            private set

        val isResolved: Boolean get() = region != null

        fun setRegion(value: String) {
            region = value
            prefs.edit().putString("region", value).apply()
        }

        fun clear() {
            region = null
            prefs.edit().clear().apply()
        }

        // ─── URL builders ────────────────────────────────────────────

        fun chatsvcBase(): String = "https://teams.microsoft.com/api/chatsvc/${region ?: DEFAULT}/v1/users/ME"

        fun csaBase(): String = "https://teams.microsoft.com/api/csa/${region ?: DEFAULT}/api/v2/teams"

        fun upsSubscriptionUrl(endpointId: String): String =
            "https://teams.cloud.microsoft/ups/${region ?: DEFAULT}/v1/pubsub/subscriptions/$endpointId"

        fun customEmojiUrl(
            tenantId: String,
            objectId: String,
        ): String = "https://${regionCode()}-prod.asyncgw.teams.microsoft.com/v1/$tenantId/objects/$objectId/views/imgt2_anim"

        fun trouterDefaultUrl(): String = "wss://go-${regionCode()}.trouter.teams.microsoft.com/v4/c"

        private fun regionCode(): String =
            when (region) {
                "amer" -> "us"
                "apac" -> "ap"
                else -> "eu"
            }

        companion object {
            const val DEFAULT = "emea"
            private val REGION_REGEX = Regex("/api/(?:csa|chatsvc)/(\\w+)/")
            val KNOWN_REGIONS = setOf("emea", "amer", "apac")

            fun extractRegionFromUrl(url: String): String? =
                REGION_REGEX
                    .find(url)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it in KNOWN_REGIONS }
        }
    }
