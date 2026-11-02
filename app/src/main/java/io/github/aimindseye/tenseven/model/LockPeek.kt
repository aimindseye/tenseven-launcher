package io.github.aimindseye.tenseven.model

/** Progressive lock notification discovery. This only chooses UI depth; it never dismisses Android's keyguard. */
object LockPeek {
    enum class Stage { COUNTS, PEEK, EXPANDED }

    fun next(current: Stage): Stage =
        when (current) {
            Stage.COUNTS -> Stage.PEEK
            Stage.PEEK -> Stage.EXPANDED
            Stage.EXPANDED -> Stage.EXPANDED
        }

    fun previous(current: Stage): Stage =
        when (current) {
            Stage.COUNTS -> Stage.COUNTS
            Stage.PEEK -> Stage.COUNTS
            Stage.EXPANDED -> Stage.PEEK
        }
}
