package io.github.aimindseye.tenseven.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.BackupActivity
import io.github.aimindseye.tenseven.HomeStyleActivity
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.AppUsage
import io.github.aimindseye.tenseven.model.AssignCommand
import io.github.aimindseye.tenseven.model.AssignRequest
import io.github.aimindseye.tenseven.model.CommandBestMatch
import io.github.aimindseye.tenseven.model.CommandGroups
import io.github.aimindseye.tenseven.model.CommandQuery
import io.github.aimindseye.tenseven.model.CommandRanking
import io.github.aimindseye.tenseven.model.CommandScope
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickActionKind
import io.github.aimindseye.tenseven.model.QuickActions
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.AppUsageRepository
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.privacy.AllAppsKeyAction
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy
import io.github.aimindseye.tenseven.privacy.PrivacySummaryPolicy
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun SearchLaunchButton(
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = t("search_command"),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = t("apps_settings_sable_actions"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SearchScreen(
    apps: List<AppEntry>,
    recentKeys: List<String>,
    letterShortcuts: LetterShortcutRepository?,
    initialQuery: String,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
    workProfilePaused: Boolean?,
    onToggleWorkProfile: () -> Unit,
    onBack: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    secondInstanceOf: (AppEntry) -> AppEntry?,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    onFrames: (() -> Unit)?,
    onGrid: () -> Unit,
    onHub: (HubFilter) -> Unit,
    onEditHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    // Seeded by type-to-launch; the cursor sits after the seed so typing continues.
    var query by remember(initialQuery) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    var chip by remember { mutableStateOf<CommandScope?>(null) }
    // The result whose actions are open ("app:<key>" or "cmd:<title>").
    var actionsFor by remember { mutableStateOf<String?>(null) }
    val typed = CommandQuery.parse(query.text)
    val parsed = CommandGroups.apply(typed, chip)
    val term = parsed.term
    val locale = Locale.getDefault()

    fun moveChip(delta: Int) {
        chip = CommandGroups.next(chip ?: parsed.scope, delta)
        actionsFor = null
    }

    val profileApps =
        when (parsed.scope) {
            CommandScope.Private -> apps.filter { it.profileLabel == AppProfiles.PRIVATE }
            CommandScope.Dual -> apps.filter { it.profileLabel == AppProfiles.DUAL }
            CommandScope.Work -> apps.filter { it.profileLabel == AppProfiles.WORK }
            else -> null
        }
    val usageContext = LocalContext.current
    val appUsage = remember(apps, recentKeys) { AppUsageRepository(usageContext).load() }
    val (filteredApps, privacyMatches) =
        remember(apps, recentKeys, parsed, appUsage) {
            if (profileApps != null) {
                CommandRanking.apps(profileApps, { it.label }, { null }, term) to emptyList()
            } else if (!parsed.includes(CommandScope.Apps)) {
                emptyList<AppEntry>() to emptyList()
            } else {
                // Recents first, label prefix before contains; apps whose effective
                // access matches privacy words ("camera", "mic") are their own group.
                val byName =
                    CommandRanking
                        .apps(
                            items = apps,
                            label = { it.label },
                            recentRank = { app -> recentKeys.indexOf(app.stableKey()).takeIf { it >= 0 } },
                            term = term,
                            frequency = { app -> appUsage.score(app.stableKey()) },
                        ).take(if (term.isEmpty()) 6 else 12)
                val byPrivacy =
                    if (term.isEmpty()) {
                        emptyList()
                    } else {
                        apps
                            .filter { app ->
                                app !in byName && PrivacySummaryPolicy.matchesPrivacyQuery(app.privacy, term)
                            }.take(8)
                    }
                byName to byPrivacy
            }
        }

    val searchContext = LocalContext.current
    val commands =
        listOf(
            SearchCommand(t("all_apps"), t("a_to_z_with_privacy"), "A", t("start"), onAllApps),
            onFrames?.let { SearchCommand(t("active_frames"), t("open_apps_home"), "▦", t("start"), it) },
            SearchCommand(t("app_grid"), t("every_app_in_your_order"), "▤", t("start"), onGrid),
            SearchCommand(t("hub_page"), t("messages_mail_calls_contacts_and"), "◇", t("start"), { onHub(HubFilter.All) }),
            SearchCommand(t("edit_home"), t("order_the_app_grid_the"), "▭", t("start"), onEditHome),
            SearchCommand(t("appearance"), t("theme_and_sable_accent"), "◐", t("start"), onAppearance),
            if (workProfilePaused != null) {
                SearchCommand(
                    searchContext.getString(if (workProfilePaused) R.string.work_resume else R.string.work_pause),
                    searchContext.getString(R.string.work_hint),
                    "▣",
                    searchContext.getString(R.string.work_source),
                    onToggleWorkProfile,
                )
            } else {
                null
            },
            SearchCommand(
                searchContext.getString(R.string.launcher_settings),
                searchContext.getString(R.string.launcher_settings_hint),
                "⚙",
                t("start"),
                { onStartIntent(Intent(searchContext, LauncherSettingsActivity::class.java)) },
            ),
            SearchCommand(
                t("home_screen_style_2"),
                searchContext.getString(R.string.style_command_hint),
                "▭",
                t("start"),
                { onStartIntent(Intent(searchContext, HomeStyleActivity::class.java)) },
            ),
            SearchCommand(
                searchContext.getString(R.string.setup_menu),
                searchContext.getString(R.string.setup_command_hint),
                "✓",
                t("start"),
                { onStartIntent(Intent(searchContext, OnboardingActivity::class.java)) },
            ),
            SearchCommand(
                searchContext.getString(R.string.setup_extras_title),
                searchContext.getString(R.string.extras_command_hint),
                "✚",
                t("start"),
                {
                    onStartIntent(
                        Intent(searchContext, OnboardingActivity::class.java)
                            .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.EXTRAS.name),
                    )
                },
            ),
            SearchCommand(
                searchContext.getString(R.string.backup_menu),
                searchContext.getString(R.string.backup_command_hint),
                "⇅",
                t("start"),
                { onStartIntent(Intent(searchContext, BackupActivity::class.java)) },
            ),
            SearchCommand(t("system_settings"), t("open_android_settings"), "⚙", t("settings"), onOpenSettings),
        ).filterNotNull()
    val filteredCommands =
        if (!parsed.includes(CommandScope.Actions) || parsed.profileScope) {
            emptyList()
        } else {
            commands.filter { command ->
                term.isEmpty() ||
                    command.title.lowercase(locale).contains(term.lowercase(locale)) ||
                    command.subtitle.lowercase(locale).contains(term.lowercase(locale))
            }
        }
    val settingsResults = if (parsed.profileScope) emptyList() else settingsResults(parsed, onStartIntent, onOpenSettings)
    val peopleResults = if (parsed.profileScope) emptyList() else peopleResults(parsed, onStartIntent, onHub)
    val hubResults = if (parsed.profileScope) emptyList() else hubResults(parsed, onHub)
    val quickRows =
        quickActionRows(searchContext, query.text, onStartIntent) +
            assignRows(searchContext, query.text, apps, appUsage, letterShortcuts)
    val firstOther =
        (quickRows + filteredCommands.takeIf { term.isNotEmpty() }.orEmpty() + settingsResults + peopleResults + hubResults)
            .firstOrNull()
    val bestIsApp = CommandBestMatch.appWins(filteredApps.firstOrNull()?.label, firstOther?.title, term)
    val bestApp = filteredApps.firstOrNull()?.takeIf { bestIsApp == true }
    val bestOther = firstOther?.takeIf { bestIsApp == false }
    val lockedPrivate = privateSpaceLocked == true && (parsed.scope == CommandScope.Private || term.isNotEmpty())

    fun appActions(app: AppEntry): List<Pair<String, () -> Unit>> =
        buildList {
            add("open" to { onLaunchApp(app) })
            secondInstanceOf(app)?.let { other ->
                add((if (other.profileLabel == AppProfiles.DUAL) t("open_dual_copy") else t("open_personal_app")) to { onLaunchApp(other) })
            }
            add(t("app_info_2") to { onOpenAppInfo(app) })
            add(t("more_actions") to { onOpenPeek(app) })
        }

    @Composable
    fun AppResult(app: AppEntry) {
        val key = "app:" + app.stableKey()
        AppRow(
            app = app,
            onClick = { onLaunchApp(app) },
            onContext = { actionsFor = if (actionsFor == key) null else key },
            modifier =
                Modifier.onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val decision = AllAppsKeyPolicy.decide(native.keyCode, 0, native.metaState, native.repeatCount > 0)
                    val action: (() -> Unit)? =
                        when (decision.action) {
                            AllAppsKeyAction.OpenSecond -> {
                                { onLaunchApp(secondInstanceOf(app) ?: app) }
                            }

                            AllAppsKeyAction.Actions -> {
                                { actionsFor = if (actionsFor == key) null else key }
                            }

                            else -> {
                                null
                            }
                        }
                    if (action != null && event.type == KeyEventType.KeyDown) action()
                    action != null
                },
        )
        if (actionsFor == key) EditOptions(appActions(app))
    }

    @Composable
    fun CommandResult(command: SearchCommand) {
        val key = "cmd:" + command.source + ":" + command.title
        SearchCommandRow(
            command = command,
            onActions = { actionsFor = if (actionsFor == key) null else key },
        )
        if (actionsFor == key) EditOptions(command.actions.ifEmpty { listOf("open" to command.onClick) })
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        MetroHeader(
            title = "search",
            onBack = onBack,
        )
        Spacer(Modifier.height(10.dp))

        SableSearchField(
            value = query,
            onValueChange = {
                query = it
                actionsFor = null
            },
            // Enter opens the best match: else the first app, else the first other result.
            onSubmit = {
                bestApp?.let(onLaunchApp)
                    ?: bestOther?.onClick?.invoke()
                    ?: filteredApps.firstOrNull()?.let(onLaunchApp)
                    ?: firstOther?.onClick?.invoke()
            },
            onGroupDelta = ::moveChip,
        )
        Spacer(Modifier.height(8.dp))
        CommandChips(selected = if (parsed.profileScope) null else parsed.scope, onSelect = { chip = it })
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        // ←→ on a result moves between the groups; ↑↓ stay one list.
                        val native = event.nativeKeyEvent
                        val delta = AppGrid.arrowDelta(native.keyCode)?.takeIf { it == -1 || it == 1 }
                        if (delta == null || native.metaState != 0) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) moveChip(delta)
                        true
                    },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (lockedPrivate) {
                item(key = "locked") {
                    PrivateLockedCard(onUnlock = onTogglePrivateSpace, full = parsed.scope == CommandScope.Private)
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (bestApp != null || bestOther != null) {
                item(key = "best") {
                    SectionLabel(t("best_match"))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp),
                    ) {
                        Column {
                            if (bestApp != null) AppResult(bestApp) else bestOther?.let { CommandResult(it) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            val apps1 = filteredApps.filterNot { it == bestApp }
            if (apps1.isNotEmpty()) {
                item {
                    SectionLabel(
                        if (profileApps !=
                            null
                        ) {
                            CommandGroups.label(parsed.scope).lowercase(locale) + " apps"
                        } else if (term.isEmpty()) {
                            t("recent_apps")
                        } else {
                            "apps"
                        },
                    )
                }
                items(apps1, key = { "a:" + it.stableKey() }) { app -> AppResult(app) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (privacyMatches.isNotEmpty()) {
                item { SectionLabel(t("permission_matches")) }
                items(privacyMatches, key = { "p:" + it.stableKey() }) { app -> AppResult(app) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            listOf(
                searchContext.getString(R.string.quick_group) to quickRows,
                "people" to peopleResults,
                "settings" to settingsResults,
                "hub" to hubResults,
                (if (term.isEmpty()) t("quick_actions") else "actions") to filteredCommands,
            ).forEach { (label, rows) ->
                val shown = rows.filterNot { it == bestOther }
                if (shown.isNotEmpty()) {
                    item { SectionLabel(label) }
                    items(shown, key = { "$label:" + it.source + it.title }) { command -> CommandResult(command) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            val noMatches =
                listOf(
                    filteredApps,
                    privacyMatches,
                    filteredCommands,
                    settingsResults,
                    quickRows,
                    peopleResults,
                    hubResults,
                ).all { it.isEmpty() }
            if (noMatches && !lockedPrivate) {
                item {
                    EmptyState(
                        t("no_local_results_match_prefixes", term),
                    )
                }
            }
            item {
                Text(
                    text = t("results_groups_enter_opens_shift"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The group chips over the results; the current scope is filled. */
@Composable
internal fun CommandChips(
    selected: CommandScope?,
    onSelect: (CommandScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CommandGroups.CHIPS.forEach { scope ->
            val on = scope == selected
            Text(
                text = CommandGroups.label(scope),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable { onSelect(scope) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Command's locked state (sable-command-titan2 "4. Locked / Private"): the
 * Private Space is locked, so its apps are neither listed nor searched.
 */
@Composable
internal fun PrivateLockedCard(
    onUnlock: () -> Unit,
    full: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
        horizontalAlignment = if (full) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (full) Text("🔒", fontSize = 28.sp)
        Text(
            text = if (full) t("content_hidden") else t("private_space_locked"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = t("unlock_to_show_and_search"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = t("unlock"),
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .sableFocusRing()
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

internal fun settingsResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Settings)) return emptyList()
    val limit = if (parsed.scope == CommandScope.Settings) 12 else 4
    return CommandRanking.settings(parsed.term, limit = limit).map { dest ->
        val open = { onStartIntent(Intent(dest.action)) }
        SearchCommand(
            title = dest.title,
            subtitle = t("open_settings", dest.title),
            mark = "⚙",
            source = t("settings"),
            onClick = open,
            actions = listOf(t("open_2", dest.title) to open, t("open_settings_home") to onOpenSettings),
        )
    }
}

/**
 * People: dial a typed number, or hand the name to Contacts' own search
 * (Start holds no contacts permission and keeps no copy).
 */
internal fun peopleResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    val term = parsed.term
    if (term.isEmpty() || !parsed.includes(CommandScope.People)) return emptyList()
    val dial =
        CommandQuery.dialable(term)?.let { number ->
            val call = { onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            SearchCommand(
                title = t("call", number),
                subtitle = t("opens_the_dialer_with_the"),
                mark = "☎",
                source = t("phone"),
                onClick = call,
                actions =
                    listOf(
                        "call" to call,
                        t("send_a_message") to { onStartIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) },
                        t("add_to_contacts") to {
                            onStartIntent(
                                Intent(Intent.ACTION_INSERT)
                                    .setType("vnd.android.cursor.dir/contact")
                                    .putExtra("phone", number),
                            )
                        },
                    ),
            )
        }
    val find = {
        onStartIntent(
            Intent(Intent.ACTION_SEARCH)
                .setPackage(CONTACTS_PACKAGE)
                .putExtra(SearchManager.QUERY, term),
        )
    }
    val contacts =
        SearchCommand(
            title = t("find_in_contacts", term),
            subtitle = t("search_people_by_name_number"),
            mark = "@",
            source = t("contacts"),
            onClick = find,
            actions = listOf(t("find_in_contacts_2") to find, t("hub_page_contacts") to { onHub(HubFilter.Contacts) }),
        ).takeIf { parsed.scope == CommandScope.People || term.length >= 2 }
    return listOfNotNull(dial, contacts)
}

/**
 * Hub results open Start's own Hub page on a tab (Start never shows message, mail or call
 * content). Shown for "/hub", or when the term names Hub or one of its tabs.
 */
internal fun hubResults(
    parsed: CommandQuery,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Hub)) return emptyList()
    val term = parsed.term.lowercase(Locale.ROOT)
    val tab = HubFilter.entries.firstOrNull { term.isNotEmpty() && it.label.lowercase(Locale.ROOT).startsWith(term) }
    val named = term.isNotEmpty() && ("hub".startsWith(term) || tab != null)
    if (parsed.scope != CommandScope.Hub && !named) return emptyList()
    val target = tab ?: HubFilter.All
    return listOf(
        SearchCommand(
            title = if (target == HubFilter.All) t("open_hub") else t("hub_2", target.label),
            subtitle = t("messages_calls_contacts_and_notifications"),
            mark = "◉",
            source = t("hub"),
            onClick = { onHub(target) },
            actions = HubFilter.entries.map { filter -> t("hub_2", filter.label) to { onHub(filter) } },
        ),
    )
}

@Composable
internal fun SableSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onGroupDelta: (Int) -> Unit,
) {
    // The field takes focus on arrival so the keyboard types straight into it.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val selection = value.selection
                            when {
                                event.type != KeyEventType.KeyDown -> {
                                    false
                                }

                                // Holding Backspace clears the whole query.
                                native.keyCode == KEYCODE_DEL && native.repeatCount > 0 -> {
                                    if (value.text.isNotEmpty()) onValueChange(TextFieldValue(""))
                                    true
                                }

                                native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && native.repeatCount == 0 -> {
                                    onSubmit()
                                    true
                                }

                                // Left at the start or Right at the end of the query: previous / next group.
                                native.metaState == 0 && selection.collapsed &&
                                    native.keyCode == KEYCODE_DPAD_LEFT && selection.start == 0 -> {
                                    onGroupDelta(-1)
                                    true
                                }

                                native.metaState == 0 && selection.collapsed &&
                                    native.keyCode == KEYCODE_DPAD_RIGHT && selection.end == value.text.length -> {
                                    onGroupDelta(1)
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }.padding(vertical = 8.dp),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(
                            text = t("search_apps_sable_actions"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
internal fun SearchCommandRow(
    command: SearchCommand,
    onActions: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .sableFocusRing()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
                    val menu = native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU || (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt)
                    if (menu && event.type == KeyEventType.KeyDown && native.repeatCount == 0) onActions()
                    menu
                }.combinedClickable(onClick = command.onClick, onLongClick = onActions)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.mark,
            modifier = Modifier.width(38.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = command.source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

internal fun quickActionRows(
    context: Context,
    raw: String,
    onStartIntent: (Intent) -> Unit,
): List<SearchCommand> {
    val source = context.getString(R.string.quick_source)
    QuickActions.parse(raw)?.let { action ->
        val (title, intent) =
            when (action.kind) {
                QuickActionKind.TODO -> {
                    context.getString(R.string.quick_todo, action.text) to
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text),
                            context.getString(R.string.quick_send_to_app),
                        )
                }

                QuickActionKind.NOTE -> {
                    context.getString(R.string.quick_note, action.text) to
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text),
                            context.getString(R.string.quick_send_to_app),
                        )
                }

                QuickActionKind.EVENT -> {
                    context.getString(R.string.quick_event, action.text) to
                        Intent(Intent.ACTION_INSERT)
                            .setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, action.text)
                }
            }
        return listOf(
            SearchCommand(title, context.getString(R.string.quick_hint), "+", source, { onStartIntent(intent) }),
        )
    }
    QuickActions.normalizedWebUrl(raw)?.let { url ->
        return listOf(
            SearchCommand(
                context.getString(R.string.quick_open_url, url),
                context.getString(R.string.quick_hint_url),
                "↗",
                source,
                { onStartIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            ),
        )
    }
    return emptyList()
}

/** `assign k camera` lists the apps that K could open; choosing one holds it on K. `unassign k` frees the letter. */
internal fun assignRows(
    context: Context,
    raw: String,
    apps: List<AppEntry>,
    usage: AppUsage,
    letterShortcuts: LetterShortcutRepository?,
): List<SearchCommand> {
    val request = AssignCommand.parse(raw) ?: return emptyList()
    val shortcuts = letterShortcuts ?: return emptyList()
    val source = context.getString(R.string.assign_source)
    val letter = request.letter.uppercaseChar()
    val current = shortcuts.load()
    return when (request) {
        is AssignRequest.Clear -> {
            val held = current.componentFor(request.letter)?.let(android.content.ComponentName::unflattenFromString)
            val label = held?.let { c -> apps.firstOrNull { it.component == c }?.label ?: c.packageName }
            if (held == null || label == null) {
                listOf(SearchCommand(context.getString(R.string.assign_nothing, letter), "", "$letter", source, {}))
            } else {
                listOf(
                    SearchCommand(
                        context.getString(R.string.assign_clear, letter, label),
                        context.getString(R.string.assign_hint, letter),
                        "$letter",
                        source,
                        {
                            shortcuts.remove(held)
                            Toast.makeText(context, context.getString(R.string.assign_cleared, letter), Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
            }
        }

        is AssignRequest.Assign -> {
            CommandRanking
                .apps(
                    items = apps.filter { it.inLauncherUser },
                    label = { it.label },
                    recentRank = { null },
                    term = request.appTerm,
                    frequency = { usage.score(it.stableKey()) },
                ).take(ASSIGN_ROWS)
                .map { app ->
                    val holder = current.componentFor(request.letter)
                    val replaces =
                        holder
                            ?.takeIf { it != app.component.flattenToString() }
                            ?.let(android.content.ComponentName::unflattenFromString)
                            ?.let { c -> apps.firstOrNull { it.component == c }?.label ?: c.packageName }
                    SearchCommand(
                        context.getString(R.string.assign_row, letter, app.label),
                        if (replaces !=
                            null
                        ) {
                            context.getString(R.string.assign_replaces, replaces)
                        } else {
                            context.getString(R.string.assign_hint, letter)
                        },
                        "$letter",
                        source,
                        {
                            shortcuts.assign(request.letter, app.component)
                            Toast.makeText(context, context.getString(R.string.assign_done, letter, app.label), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
        }
    }
}
