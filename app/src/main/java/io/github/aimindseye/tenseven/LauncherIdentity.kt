package io.github.aimindseye.tenseven

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The visible launcher name: TenSeven by default, replaced by the SableOS identity overlay. */
@Composable
internal fun appName(): String = stringResource(R.string.tenseven_app_name)
