package io.github.aimindseye.tenseven.onboarding

/**
 * The setup steps, in the order they are offered. Only [HOME] is needed to be a launcher; every other step
 * is optional, switches on one feature, and can be skipped or done later. The flow follows the shape of
 * MinkLauncher's onboarding (home role first, then each permission with its reason); the code is Tenseven's own.
 */
enum class SetupStep {
    HOME,
    STYLE,
    CONTACTS,
    PHOTOS,
    NOTIFICATIONS,
    USAGE,
    LOOK,
    GRID,
    WIDGETS,
    HUB,
    LOCK,
    EXTRAS,
    ;

    val required: Boolean get() = this == HOME

    /**
     * A step of choices rather than one permission: its controls are on the step itself and apply at once, so it is
     * always offered and never "not done". Every setting in Launcher Settings has such a step, so a feature added there
     * is part of the first-run setup too.
     */
    val isChoice: Boolean get() = this in CHOICES

    /** Android calls these "special access": granted in Settings, not by a permission dialog. */
    val isSpecialAccess: Boolean get() = this == NOTIFICATIONS || this == USAGE

    companion object {
        private val CHOICES = setOf(STYLE, LOOK, GRID, WIDGETS, HUB, LOCK)
    }
}

/** What is already granted. */
data class SetupState(
    val homeRoleHeld: Boolean,
    val contactsGranted: Boolean,
    val photosGranted: Boolean,
    val notificationAccess: Boolean,
    val usageAccess: Boolean,
    /** At least one bundled extra app (Calculator, Notes, a game) is switched on. Off by default. */
    val extrasChosen: Boolean = false,
    /** False in the lite build, which has no notification listener to turn on. */
    val notificationListenerAvailable: Boolean = true,
) {
    fun isDone(step: SetupStep): Boolean =
        when (step) {
            SetupStep.HOME -> homeRoleHeld
            SetupStep.STYLE, SetupStep.LOOK, SetupStep.GRID, SetupStep.WIDGETS, SetupStep.HUB, SetupStep.LOCK -> true
            SetupStep.CONTACTS -> contactsGranted
            SetupStep.PHOTOS -> photosGranted
            SetupStep.NOTIFICATIONS -> notificationAccess
            SetupStep.USAGE -> usageAccess
            SetupStep.EXTRAS -> extrasChosen
        }
}

object OnboardingPlan {
    /** Android 13 added "restricted settings" for apps that were not installed from a store. */
    const val RESTRICTED_SETTINGS_SDK = 33

    private const val PLAY_STORE = "com.android.vending"

    /** The steps still to do, in order. Empty when everything is set up. */
    fun pending(state: SetupState): List<SetupStep> =
        SetupStep.entries
            .filterNot { it == SetupStep.NOTIFICATIONS && !state.notificationListenerAvailable }
            .filter { it.isChoice || !state.isDone(it) }

    /**
     * Whether to explain "Allow restricted settings". On Android 13 or newer, the switch for notification or
     * usage access can be greyed out for an app installed from a downloaded APK; the way round it is in App info.
     * Shown as a hint, not as a certainty, since Android also lifts the restriction for some other installers.
     */
    fun needsRestrictedSettingsHelp(
        step: SetupStep,
        sdkInt: Int,
        installer: String?,
    ): Boolean = step.isSpecialAccess && sdkInt >= RESTRICTED_SETTINGS_SDK && installer != PLAY_STORE
}
