package com.squads.app.auth

/**
 * OAuth configuration for Microsoft Teams authentication.
 * Device code OAuth flow with the official Teams client ID.
 */
object OAuthConfig {
    const val CLIENT_ID = "1fec8e78-bce4-4aaf-ab1b-5451cc387264"
    const val TENANT = "organizations"

    /** OAuth v1 device code endpoint (same as CLI) */
    fun deviceCodeUrl(): String =
        "https://login.microsoftonline.com/$TENANT/oauth2/devicecode"

    /** OAuth v1 token endpoint for polling device code (same as CLI) */
    fun tokenUrl(): String =
        "https://login.microsoftonline.com/$TENANT/oauth2/token"

    /** OAuth v2 token endpoint for refresh token renewal */
    fun tokenV2Url(): String =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token"

    /** Device code request body — resource=skype API (same as CLI) */
    fun deviceCodeBody(): String =
        "client_id=$CLIENT_ID&resource=https://api.spaces.skype.com"

    /** Token polling body with device code */
    fun tokenPollBody(deviceCode: String): String =
        "client_id=$CLIENT_ID&code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
}
