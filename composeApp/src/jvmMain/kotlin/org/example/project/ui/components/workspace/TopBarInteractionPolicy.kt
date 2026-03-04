package org.example.project.ui.components.workspace

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Immutable

enum class TopBarHitTarget {
    NonInteractive,
    Interactive,
}

@Immutable
data class TopBarInteractionPolicy(
    val allowDoubleClickToggleOnNonInteractive: Boolean = true,
) {
    fun canToggleWindowOnDoubleClick(target: TopBarHitTarget): Boolean {
        return allowDoubleClickToggleOnNonInteractive && target == TopBarHitTarget.NonInteractive
    }
}

fun Modifier.windowToggleOnDoubleClick(
    enabled: Boolean,
    onToggle: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onToggle) {
        detectTapGestures(
            onDoubleTap = { onToggle() },
        )
    }
}
