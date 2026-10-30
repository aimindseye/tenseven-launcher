package io.github.aimindseye.tenseven.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.WidgetSizing
import io.github.aimindseye.tenseven.platform.WidgetHostController

/**
 * The Widgets page: other apps' Android widgets in one scrolling column, at the width of the page and the
 * height each widget asks for. Edit mode puts Up, Down and Remove over each widget (a widget is drawn by
 * another app and takes touches itself, so the controls cannot sit on it any other way).
 */
@Composable
internal fun WidgetsPage(
    controller: WidgetHostController?,
    ids: List<Int>,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.widgets_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (controller != null && ids.isNotEmpty()) {
                PageButton(stringResource(if (editing) R.string.widgets_done else R.string.widgets_edit)) { editing = !editing }
            }
            if (controller != null) PageButton(stringResource(R.string.widgets_add)) { picking = true }
        }
        if (controller == null || ids.isEmpty()) {
            Text(
                stringResource(R.string.widgets_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ids.forEach { id -> WidgetItem(controller, id, editing) }
            }
        }
    }
    if (picking && controller != null) {
        WidgetPicker(
            providers = controller.providers(),
            onPick = {
                picking = false
                controller.begin(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun WidgetItem(
    controller: WidgetHostController,
    id: Int,
    editing: Boolean,
) {
    val density = LocalDensity.current.density
    val info = controller.info(id)
    val height = WidgetSizing.heightDp(info?.minHeight ?: 0, density)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
    ) {
        val widthDp = maxWidth.value.toInt()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> controller.createView(id) ?: FrameLayout(context) },
            update = { view ->
                // Tell the widget how much room it has, so one that lays itself out by size picks the right layout.
                if (view is AppWidgetHostView) {
                    runCatching { view.updateAppWidgetSize(null, widthDp, height, widthDp, height) }
                }
            },
        )
        if (editing) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                PageButton(stringResource(R.string.widgets_up)) { controller.move(id, -1) }
                PageButton(stringResource(R.string.widgets_down)) { controller.move(id, 1) }
                PageButton(stringResource(R.string.widgets_remove)) { controller.remove(id) }
            }
        }
    }
}

@Composable
private fun WidgetPicker(
    providers: List<AppWidgetProviderInfo>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val rows =
        remember(providers) {
            val pm = context.packageManager
            providers
                .map { info ->
                    val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    val name = runCatching { info.loadLabel(pm) }.getOrDefault(info.provider.shortClassName)
                    PickerRow(info, app.getOrDefault(info.provider.packageName), name)
                }.sortedWith(compareBy({ it.app.lowercase() }, { it.name.lowercase() }))
        }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.widgets_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.widgets_none_installed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(rows, key = { it.info.provider.flattenToString() + it.info.profile }) { row ->
                            val icon =
                                remember(row.info) {
                                    runCatching {
                                        row.info
                                            .loadIcon(context, 0)
                                            .toBitmap(96, 96)
                                            .asImageBitmap()
                                    }.getOrNull()
                                }
                            Surface(
                                onClick = { onPick(row.info) },
                                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(row.name, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            row.app + " · " +
                                                WidgetSizing.cells(row.info.minWidth, density) + " × " +
                                                WidgetSizing.cells(row.info.minHeight, density),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                PageButton(stringResource(R.string.widgets_cancel), onDismiss)
            }
        }
    }
}

private data class PickerRow(
    val info: AppWidgetProviderInfo,
    val app: String,
    val name: String,
)

@Composable
private fun PageButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
