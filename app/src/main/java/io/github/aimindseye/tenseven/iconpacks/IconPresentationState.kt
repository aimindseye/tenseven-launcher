package io.github.aimindseye.tenseven.iconpacks

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.model.IconPresentation
import io.github.aimindseye.tenseven.platform.StartStateRepository

/** In-process Compose state, persisted without ever installing/replacing another app's icons. */
object IconPresentationState {
    var selected: IconPresentation by mutableStateOf(IconPresentation.TenSeven)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        selected = StartStateRepository(context).loadIconPresentation()
        initialized = true
    }

    fun choose(
        context: Context,
        next: IconPresentation,
    ) {
        selected = next
        StartStateRepository(context).saveIconPresentation(next)
    }
}
