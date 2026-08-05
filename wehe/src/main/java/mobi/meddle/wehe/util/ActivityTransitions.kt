package mobi.meddle.wehe.util

import android.app.Activity
import android.os.Build

/**
 * Helpers for the slide animations used when moving between activities.
 *
 * [Activity.overridePendingTransition] was deprecated in API 34 in favour of
 * [Activity.overrideActivityTransition]. The two work differently: the old call is made by whoever
 * triggers the change, right after startActivity()/finish(), while the new one is registered up
 * front on the activity whose own open/close animation is being customized. So on API 34+ the
 * activity registers its animations once via [registerTransitions], and on older platforms the
 * call sites keep applying them through [applyLegacyTransition].
 */
fun Activity.registerTransitions(
    openEnterAnim: Int,
    openExitAnim: Int,
    closeEnterAnim: Int,
    closeExitAnim: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, openEnterAnim, openExitAnim)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, closeEnterAnim, closeExitAnim)
    }
}

/**
 * Applies a transition on platforms older than API 34, where animations have to be set right after
 * the startActivity()/finish() call that triggers them. A no-op on newer platforms, which get their
 * animations from [registerTransitions] instead.
 */
@Suppress("DEPRECATION") // overrideActivityTransition() is used on API 34+, see registerTransitions
fun Activity.applyLegacyTransition(enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overridePendingTransition(enterAnim, exitAnim)
    }
}
