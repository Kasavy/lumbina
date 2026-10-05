package dev.lumbina.tv.core.domain.access

/** What the user did with a runtime permission's system dialog. */
enum class PermissionAnswer {
    Granted,

    /** Said no; asking again may show the dialog again. */
    Denied,

    /** Said no for good: the system answers without a dialog from now on. Only its settings can grant it. */
    DeniedForGood,

    /** Gave no answer: the dialog went away (Back, the screensaver coming on). Asking later is fair. */
    NoAnswer,
    ;

    /** Whether the user decided, so an ask-once flow should stop asking. */
    val answered: Boolean get() = this != NoAnswer

    companion object {
        /**
         * Reads the result of a request. [rationaleBefore] and [rationaleAfter] are the system's
         * "should show rationale" around it (true once the user said no and may still be asked).
         * A refusal with no rationale either side is either a dialog the user left without
         * answering, or the system refusing at once because the user chose not to be asked: the
         * system's answer comes back without a dialog, faster than [NO_DIALOG_MILLIS].
         */
        fun of(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean, millis: Long): PermissionAnswer =
            when {
                granted -> Granted
                rationaleAfter -> Denied
                rationaleBefore -> DeniedForGood
                millis < NO_DIALOG_MILLIS -> DeniedForGood
                else -> NoAnswer
            }

        /** Quicker than anyone can read a dialog and press a button. */
        const val NO_DIALOG_MILLIS = 400L
    }
}
