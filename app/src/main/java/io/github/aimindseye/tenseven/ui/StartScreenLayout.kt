package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.HomeApps
import io.github.aimindseye.tenseven.model.HomeViewport
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.stableKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class StartGridGeometry(
    val columns: Int,
    val rows: Int,
    val iconScale: Float,
    val perPage: Int,
    val pages: Int,
)

internal data class StartGridLayout(
    val appByKey: Map<String, AppEntry>,
    val candidates: List<AppEntry>,
    val defaultHomeKeys: List<String>,
    val keys: List<String>,
    val apps: List<AppEntry>,
    val labels: Set<String>,
    val quickSlots: List<QuickSlot>,
    val geometry: StartGridGeometry,
)

@Composable
internal fun rememberStartGrid(
    inputs: StartScreenInputs,
    home: StartHomeEditState,
): StartGridLayout {
    val appByKey = inputs.apps.associateBy { it.stableKey() }
    val candidates = inputs.apps.filterNot { it.profileLabel == PROFILE_PRIVATE_LABEL }
    val context = LocalContext.current
    val defaultHomeKeys = remember(inputs.apps) { defaultHomeApps(inputs.apps, context).map { it.stableKey() } }
    val keys =
        AppGrid.resolve(
            saved = home.savedGridOrder,
            preferredFirst = HomeApps.resolve(inputs.initialHomeApps, inputs.initialStartTileKeys, defaultHomeKeys),
            installed = candidates.map { it.stableKey() },
        )
    val apps = keys.mapNotNull(appByKey::get)
    val quickSlots = home.quickBarTokens.mapNotNull { resolveQuickSlot(it, inputs.apps, appByKey) }
    val fontScale = LocalDensity.current.fontScale
    val columns = HomeViewport.columns(startGridWidthDp(), inputs.gridColumnsSetting, fontScale)
    val iconScale = AppGrid.iconScale(inputs.gridIconSize)
    val rows = HomeViewport.rows(startGridHeightDp(), (GRID_CELL_MIN_DP * iconScale).toInt(), fontScale)
    val perPage = AppGrid.perPage(rows, columns)
    return StartGridLayout(
        appByKey,
        candidates,
        defaultHomeKeys,
        keys,
        apps,
        apps.mapTo(HashSet()) { it.label },
        quickSlots,
        StartGridGeometry(columns, rows, iconScale, perPage, AppGrid.pageCount(apps.size, perPage)),
    )
}

/** Logical page numbers are distinct from absolute pager indices when Widgets adds a leading page. */
internal object StartPagerPolicy {
    fun initialPage(
        screen: String,
        lead: Int,
    ): Int = StartPages.forScreen(screen).let { if (it < 0 && lead == 0) StartPages.HOME else it } + lead

    fun moveTarget(
        current: Int,
        delta: Int,
        pageCount: Int,
    ): Int = (current + delta).coerceIn(0, pageCount - 1)
}

internal class StartPager(
    val state: PagerState,
    val lead: Int,
    val pageCount: Int,
    val scope: CoroutineScope,
) {
    val currentPage: Int get() = state.currentPage - lead
    val settledPage: Int get() = state.settledPage - lead

    fun movePage(delta: Int) {
        animateAbsolute(StartPagerPolicy.moveTarget(state.currentPage, delta, pageCount))
    }

    fun animateLogical(page: Int) {
        animateAbsolute(page + lead)
    }

    fun animateAbsolute(page: Int) {
        scope.launch { state.animateScrollToPage(page) }
    }

    fun scrollHome() {
        scope.launch { state.scrollToPage(StartPages.HOME + lead) }
    }
}

@Composable
internal fun rememberStartPager(
    inputs: StartScreenInputs,
    gridPages: Int,
): StartPager {
    val lead = if (inputs.widgetsPage) 1 else 0
    val pageCount = StartPages.count(gridPages) + lead
    val state = rememberPagerState(initialPage = StartPagerPolicy.initialPage(inputs.initialScreen, lead)) { pageCount }
    val scope = rememberCoroutineScope()
    return remember(state, lead, pageCount, scope) { StartPager(state, lead, pageCount, scope) }
}
