package com.khalinux.android.red

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistência local de metadados. Nunca grava corpos HTTP ou valores de formulários. */
class RedSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("red_sessions_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun save(session: RedSession) {
        val root = JSONObject().put("id", session.id)
            .put("operationId", session.operationId)
            .put("startedAt", session.startedAt)
            .put("endedAt", session.endedAt ?: JSONObject.NULL)
            .put("state", session.state.name)
            .put("portalMode", session.portalMode.name)
            .put("completedSteps", JSONArray(session.completedSteps.toList()))
        val events = JSONArray()
        session.events.takeLast(MAX_EVENTS).forEach { event ->
            events.put(JSONObject()
                .put("id", event.id)
                .put("sessionId", event.sessionId)
                .put("type", event.type.name)
                .put("timestamp", event.timestamp)
                .put("stepId", event.stepId ?: JSONObject.NULL)
                .put("detail", event.detail.take(240))
                .put("sequence", event.sequence)
                .put("source", event.source ?: JSONObject.NULL)
                .put("httpMethod", event.httpMethod ?: JSONObject.NULL)
                .put("route", event.route?.take(160) ?: JSONObject.NULL)
                .put("httpStatus", event.httpStatus ?: JSONObject.NULL)
                .put("bytesReceived", event.bytesReceived ?: JSONObject.NULL)
                .put("cookieChoice", event.cookieChoice?.name ?: JSONObject.NULL)
                .put("cookieAnalytics", event.cookieAnalytics ?: JSONObject.NULL)
                .put("cookiePreferences", event.cookiePreferences ?: JSONObject.NULL)
                .put("cookiePersonalization", event.cookiePersonalization ?: JSONObject.NULL)
                .put("webPermissionResource", event.webPermissionResource?.name ?: JSONObject.NULL)
                .put("webPermissionState", event.webPermissionState?.name ?: JSONObject.NULL)
                .put("responseSent", event.responseSent ?: JSONObject.NULL)
                .put("rejectionReason", event.rejectionReason ?: JSONObject.NULL)
                .put("serverFailure", event.serverFailure ?: JSONObject.NULL))
        }
        root.put("events", events)
        preferences.edit().putString(session.id, root.toString()).apply()
        val oldest = preferences.all.entries.mapNotNull { entry ->
            runCatching { entry.key to JSONObject(entry.value as String).getLong("startedAt") }.getOrNull()
        }.sortedByDescending { it.second }.drop(MAX_SESSIONS).map { it.first }
        if (oldest.isNotEmpty()) {
            val editor = preferences.edit()
            oldest.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    @Synchronized
    fun all(): List<RedSession> = preferences.all.values.mapNotNull { raw ->
        runCatching {
            val root = JSONObject(raw as String)
            val steps = root.optJSONArray("completedSteps") ?: JSONArray()
            val events = root.optJSONArray("events") ?: JSONArray()
            RedSession(
                id = root.getString("id"),
                operationId = root.getString("operationId"),
                startedAt = root.getLong("startedAt"),
                endedAt = if (root.isNull("endedAt")) null else root.getLong("endedAt"),
                state = RedOperationState.valueOf(root.getString("state")),
                completedSteps = (0 until steps.length()).map { steps.getString(it) }.toSet(),
                portalMode = runCatching { RedPortalMode.valueOf(root.optString("portalMode", RedPortalMode.LOCAL_LOGIN.name)) }.getOrDefault(RedPortalMode.LOCAL_LOGIN),
                events = (0 until events.length()).map { i ->
                    val item = events.getJSONObject(i)
                    RedEvent(
                        id = item.getString("id"),
                        sessionId = item.getString("sessionId"),
                        type = RedEventType.valueOf(item.getString("type")),
                        timestamp = item.getLong("timestamp"),
                        stepId = if (item.isNull("stepId")) null else item.getString("stepId"),
                        detail = item.getString("detail"),
                        sequence = item.optInt("sequence", i + 1),
                        source = item.optString("source").takeIf { it.isNotBlank() && it != "null" },
                        httpMethod = item.optString("httpMethod").takeIf { it.isNotBlank() && it != "null" },
                        route = item.optString("route").takeIf { it.isNotBlank() && it != "null" },
                        httpStatus = if (item.isNull("httpStatus")) null else item.optInt("httpStatus"),
                        bytesReceived = if (item.isNull("bytesReceived")) null else item.optInt("bytesReceived"),
                        cookieChoice = if (item.isNull("cookieChoice")) null else runCatching { RedCookieChoice.valueOf(item.getString("cookieChoice")) }.getOrNull(),
                        cookieAnalytics = if (item.isNull("cookieAnalytics")) null else item.getBoolean("cookieAnalytics"),
                        cookiePreferences = if (item.isNull("cookiePreferences")) null else item.getBoolean("cookiePreferences"),
                        cookiePersonalization = if (item.isNull("cookiePersonalization")) null else item.getBoolean("cookiePersonalization"),
                        webPermissionResource = if (item.isNull("webPermissionResource")) null else runCatching { RedWebPermissionResource.valueOf(item.getString("webPermissionResource")) }.getOrNull(),
                        webPermissionState = if (item.isNull("webPermissionState")) null else runCatching { RedWebPermissionState.valueOf(item.getString("webPermissionState")) }.getOrNull(),
                        responseSent = if (item.isNull("responseSent")) null else item.getBoolean("responseSent"),
                        rejectionReason = if (item.isNull("rejectionReason")) null else item.getString("rejectionReason"),
                        serverFailure = if (item.isNull("serverFailure")) null else item.getBoolean("serverFailure")
                    )
                }
            )
        }.getOrNull()
    }.sortedByDescending { it.startedAt }

    fun deleteAll() { preferences.edit().clear().apply() }

    companion object { private const val MAX_EVENTS = 300; private const val MAX_SESSIONS = 100 }
}
