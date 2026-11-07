package io.github.aimindseye.tenseven.model

enum class HubActionInput { None, Text, Unsupported }

data class HubActionCapability(
    val label: String,
    val hasTarget: Boolean,
    val input: HubActionInput,
    val alreadyHandled: Boolean = false,
)

/** The source app supplies actions; message type alone never invents Reply All, Forward or Delete. */
object HubActionPolicy {
    fun visibleIndices(
        actions: List<HubActionCapability>,
        locked: Boolean,
    ): List<Int> =
        if (locked) {
            emptyList()
        } else {
            actions.mapIndexedNotNull { index, action ->
                val namedTarget = action.hasTarget && action.label.isNotBlank()
                index.takeIf { namedTarget && action.input != HubActionInput.Unsupported && !action.alreadyHandled }
            }
        }

    fun panelTitle(email: Boolean): String = if (email) "Email options" else "Message options"

    fun openLabel(email: Boolean): String = if (email) "Open email app" else "Open messaging app"
}
