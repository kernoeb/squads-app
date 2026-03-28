package com.squads.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Safe string extraction — returns [fallback] for null and JSON-null values. */
fun JSONObject.str(
    key: String,
    fallback: String = "",
): String {
    if (isNull(key)) return fallback
    return optString(key, fallback)
}

/** Iterate a JSONArray as a list of JSONObjects. */
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
