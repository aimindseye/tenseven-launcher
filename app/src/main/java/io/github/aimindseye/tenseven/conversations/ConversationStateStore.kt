package io.github.aimindseye.tenseven.conversations

import android.content.Context

/** Keeps [ConversationState] (hashes and times only) in Tenseven's own preferences. */
internal class ConversationStateStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences("conversation_state", Context.MODE_PRIVATE)

    fun load(): ConversationState = ConversationState.decode(preferences.getString(KEY, null))

    fun save(state: ConversationState) {
        preferences.edit().putString(KEY, state.encode()).apply()
    }

    private companion object {
        const val KEY = "state"
    }
}
