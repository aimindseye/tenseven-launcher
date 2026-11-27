package io.github.aimindseye.tenseven.conversations

import android.content.Context
import io.github.aimindseye.tenseven.model.HubMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val STORAGE_APP = "app.chat"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SableHubStorageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun note(
        key: String,
        at: Long,
        pkg: String = STORAGE_APP,
    ): HubNotification {
        val message = ConversationMessage("m-$key", "c-$key", key, pkg, "Chat", "hi $key", at, "Ann", false)
        return HubNotification(
            key, "c-$key", "Ann", pkg, "Chat", at, false, listOf(message), null, null, HubMessageKind.Message,
        )
    }

    private fun chat(
        id: String,
        notification: HubNotification,
    ) = HubConversation(id, "Ann", listOf(notification), notification.messages)

    @Before
    fun reset() {
        SableHubHistory.init(context)
        SableHubOptions.set(context, SableHubOptions.KEY_KEEP, true)
        SableHubOptions.set(context, SableHubOptions.KEY_PER_MESSAGE, false)
        SableHubOptions.set(context, SableHubOptions.KEY_SYSTEM, true)
        SableHubHistory.clear()
    }

    @Test
    fun optionsDefaultAndToggle() {
        assertTrue(SableHubOptions.keep)
        SableHubOptions.set(context, SableHubOptions.KEY_PER_MESSAGE, true)
        assertTrue(SableHubOptions.perMessage)
        SableHubOptions.set(context, SableHubOptions.KEY_SYSTEM, false)
        assertFalse(SableHubOptions.system)
    }

    @Test
    fun historyKeepsWhatTheShadeDropsUntilDeleted() {
        val a = note("a", 10)
        SableHubHistory.record(listOf(a))
        assertEquals(listOf("a"), SableHubHistory.view(emptyList()).map { it.key })
        SableHubHistory.delete(listOf(a))
        assertTrue(SableHubHistory.view(emptyList()).isEmpty())
    }

    @Test
    fun forgetDropsAnAppsKeptItems() {
        SableHubHistory.record(listOf(note("a", 10), note("b", 20, "app.other")))
        SableHubHistory.forget(setOf("app.other"))
        assertEquals(listOf("a"), SableHubHistory.view(emptyList()).map { it.key })
    }

    @Test
    fun turningKeepOffClearsTheHistory() {
        SableHubHistory.record(listOf(note("a", 10)))
        SableHubOptions.set(context, SableHubOptions.KEY_KEEP, false)
        SableHubOptions.set(context, SableHubOptions.KEY_KEEP, true)
        assertTrue(SableHubHistory.view(emptyList()).isEmpty())
    }

    @Test
    fun rowsSplitOnlyWhenEveryMessageOnItsOwnIsOn() {
        val twoMessages =
            chat("c", note("a", 10)).copy(
                messages =
                    listOf(
                        ConversationMessage("m1", "c", "a", STORAGE_APP, "Chat", "one", 1, "Ann", false),
                        ConversationMessage("m2", "c", "a", STORAGE_APP, "Chat", "two", 2, "Ann", false),
                    ),
            )
        assertEquals(1, SableHubHistory.rows(listOf(twoMessages)).size)
        SableHubOptions.set(context, SableHubOptions.KEY_PER_MESSAGE, true)
        assertEquals(2, SableHubHistory.rows(listOf(twoMessages)).size)
    }

    @Test
    fun priorityPeopleAndAppsToggle() {
        val conversation = chat("c1", note("a", 10))
        assertFalse(SableHubOptions.isPriority(conversation.copy(messages = emptyList())))
        SableHubOptions.togglePerson(context, conversation)
        assertTrue(SableHubOptions.isPriorityPerson(conversation))
        SableHubOptions.togglePerson(context, conversation)
        assertFalse(SableHubOptions.isPriorityPerson(conversation))
        SableHubOptions.toggleApp(context, STORAGE_APP)
        assertTrue(SableHubOptions.isPriority(conversation))
        SableHubOptions.toggleApp(context, STORAGE_APP)
        assertFalse(SableHubOptions.isPriority(conversation.copy(messages = emptyList())))
    }
}
