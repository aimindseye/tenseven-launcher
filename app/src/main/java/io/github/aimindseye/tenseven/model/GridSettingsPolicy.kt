package io.github.aimindseye.tenseven.model

/** Style-specific controls must not leak into the other Home style.
 * Icon scale, columns and labels are currently shared across BB7 and BB10.
 */
object GridSettingsPolicy {
    fun showBb7OnlyOptions(style: HomeStyle): Boolean = style == HomeStyle.Bb7
    fun showSharedGridOptions(style: HomeStyle): Boolean =
        style == HomeStyle.Bb7 || style == HomeStyle.Bb10
}
