package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import io.github.aimindseye.tenseven.design.SableFocusMemory
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository

internal class StartActionFixture(
    context: Context,
    inventory: List<AppEntry> = emptyList(),
) {
    val pages = mutableListOf<Int>()
    val intents = mutableListOf<Intent>()
    val launched = mutableListOf<AppEntry>()
    val writes = mutableListOf<String>()
    val barWrites = mutableListOf<List<String>?>()
    val writeDestinations = mutableListOf<SableStartDestination>()
    var launchAccepted = true
    val letters = LetterShortcutRepository(context)
    val session =
        StartSessionState(
            StartNavigationState(
                mutableStateOf(SableStartDestination.Start),
                mutableStateOf(SableStartDestination.Start),
                mutableStateOf(null),
                mutableStateOf(null),
                mutableStateOf(""),
                mutableStateOf(HubFilter.All),
                mutableStateOf(false),
                mutableStateOf(0),
            ),
            StartHomeEditState(
                mutableStateOf(null),
                mutableStateOf(QuickBar.DEFAULT),
                mutableStateOf(GlanceWidgets.DEFAULT),
                mutableStateOf(HomeLayout.DEFAULT_HIDDEN),
            ),
            StartLaunchState(mutableStateOf(0), mutableStateOf(emptyList())),
            SableFocusMemory(),
        )
    val navigation = StartNavigation(session.navigation, { pages += it }, context, { intents += it })
    val applications =
        StartApplicationActions(inventory, session.launches, navigation) {
            launched += it
            launchAccepted
        }
    val home =
        StartHomeActions(
            StartHomeBindings(
                { record("grid") },
                {
                    barWrites += it
                    record("bar")
                },
                { record("widgets") },
                { record("hidden") },
                { intents += it },
                letters,
            ),
            session,
            emptyList(),
            navigation,
            applications,
            { record("scroll") },
        )

    init {
        context.getSharedPreferences("letter_shortcuts", Context.MODE_PRIVATE).edit(commit = true) { clear() }
    }

    private fun record(name: String) {
        writes += name
        writeDestinations += session.navigation.destination
    }

    fun app(name: String): AppEntry =
        AppEntry(
            name,
            ComponentName("fixture." + name, "fixture." + name + ".Main"),
            Process.myUserHandle(),
        )
}
