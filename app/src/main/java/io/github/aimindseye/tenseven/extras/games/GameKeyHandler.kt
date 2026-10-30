// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/games/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Routes key-downs to [onCommand] while this game screen is shown; false leaves the key to the focused view. */
@Composable
internal fun GameKeyHandler(
    game: GameKind,
    onCommand: (GameCommand) -> Boolean,
) {
    val host = LocalGameKeyHost.current
    val latest by rememberUpdatedState(onCommand)
    DisposableEffect(host, game) {
        host.handler = { press -> GameKeymap.command(game, press)?.let { latest(it) } ?: false }
        onDispose { host.handler = null }
    }
}
