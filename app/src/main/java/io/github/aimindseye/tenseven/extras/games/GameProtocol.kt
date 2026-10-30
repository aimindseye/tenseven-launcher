// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/games/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.games

internal fun requireOk(protocol: String): String {
    check(protocol.startsWith("OK:")) { protocol }
    return protocol.removePrefix("OK:")
}

internal fun humanize(protocol: String): String =
    protocol
        .removePrefix("ERR:")
        .replace('_', ' ')
