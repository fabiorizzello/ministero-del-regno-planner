package org.example.project.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

fun Modifier.handCursorOnHover(enabled: Boolean = true): Modifier {
    return if (enabled) {
        this.pointerHoverIcon(PointerIcon.Hand)
    } else {
        this
    }
}

