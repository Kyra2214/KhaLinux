package com.khalinux.android.red

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistência local mínima: não recebe nem grava valores de senha, tokens ou cookies. */
class RedSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("red_sessions_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun save(session: RedSession) {
        val root = JSONObject().put("id", session.id)
            .put("operationId", session.operationId)
            .put("startedAt", session.startedAt)
            .put("endedAt", session.endedAt ?: JSONObject.NULL)
            .put("state", session.state.name)
            .put("completedSteps", JSONArray(session.completedSteps.toList()))
        val events = JSONArray()
        session.events.takeLast(MAX_EVENTS).forEach { event ->
            events.put(JSONObject().put("id", event.id).put("sessionId", event.sessionId)
                .put("type", event.type.name).put("timestamp", event.timestamp)
                .put("stepId", event.stepId ?: JSONObject.NULL).put("detail", event.detail.take(240)))
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
                events = (0 until events.length()).map { i ->
                    val item = events.getJSONObject(i)
                    RedEvent(item.getString("id"), item.getString("sessionId"), RedEventType.valueOf(item.getString("type")),
                        item.getLong("timestamp"), if (item.isNull("stepId")) null else item.getString("stepId"), item.getString("detail"))
                }
            )
        }.getOrNull()
    }.sortedByDescending { it.startedAt }

    fun deleteAll() { preferences.edit().clear().apply() }

    companion object { private const val MAX_EVENTS = 300; private const val MAX_SESSIONS = 100 }
}
