package org.example.project.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import mu.KotlinLogging
import java.awt.MouseInfo
import java.awt.Window

private val logger = KotlinLogging.logger("NativeWindowDrag")

private interface Win32User32 : Library {
    companion object {
        val INSTANCE: Win32User32 by lazy { Native.load("user32", Win32User32::class.java) }
    }
    fun ReleaseCapture(): Boolean
}

private interface X11Lib : Library {
    companion object {
        val INSTANCE: X11Lib by lazy { Native.load("X11", X11Lib::class.java) }
    }

    fun XOpenDisplay(displayName: String?): Pointer?
    fun XCloseDisplay(display: Pointer): Int
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XInternAtom(display: Pointer, atomName: String, onlyIfExists: Boolean): NativeLong
    fun XSendEvent(display: Pointer, window: NativeLong, propagate: Boolean, eventMask: NativeLong, event: Pointer): Int
    fun XFlush(display: Pointer): Int
    fun XUngrabPointer(display: Pointer, time: NativeLong): Int
}

object NativeWindowDrag {
    private val isLinux = System.getProperty("os.name").lowercase().startsWith("linux")
    private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

    /** Returns true if the WM took over drag, false if caller should handle it. */
    fun tryStart(window: Window, screenX: Int, screenY: Int): Boolean = when {
        isLinux -> tryLinux(window, screenX, screenY)
        isWindows -> tryWindows(window)
        else -> false
    }

    private fun tryLinux(window: Window, screenX: Int, screenY: Int): Boolean = runCatching {
        val x11 = X11Lib.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return false
        try {
            val xid = Native.getComponentID(window)
            logger.debug("tryLinux: xid={} screenX={} screenY={}", xid, screenX, screenY)
            if (xid == 0L) return false

            val rootWindow = x11.XDefaultRootWindow(display)
            val moveResize = x11.XInternAtom(display, "_NET_WM_MOVERESIZE", false)

            // XClientMessageEvent layout on 64-bit Linux (total 96 bytes):
            //   offset  0: type          (int,     4 bytes)
            //   offset  4: [padding]     (         4 bytes)
            //   offset  8: serial        (ulong,   8 bytes)
            //   offset 16: send_event    (int,     4 bytes)
            //   offset 20: [padding]     (         4 bytes)
            //   offset 24: display       (ptr,     8 bytes)
            //   offset 32: window        (ulong,   8 bytes)
            //   offset 40: message_type  (ulong,   8 bytes)
            //   offset 48: format        (int,     4 bytes)
            //   offset 52: [padding]     (         4 bytes)
            //   offset 56: data.l[0..4]  (long[5], 40 bytes)
            val event = Memory(96)
            event.clear()
            event.setInt(0, 33)                     // type = ClientMessage
            event.setLong(8, 0L)                    // serial
            event.setInt(16, 0)                     // send_event = False
            event.setPointer(24, display)           // display address
            event.setLong(32, xid)                  // window XID
            event.setLong(40, moveResize.toLong())  // message_type = _NET_WM_MOVERESIZE
            event.setInt(48, 32)                    // format
            event.setLong(56, screenX.toLong())     // data.l[0] = x_root
            event.setLong(64, screenY.toLong())     // data.l[1] = y_root
            event.setLong(72, 8L)                   // data.l[2] = _NET_WM_MOVERESIZE_MOVE
            event.setLong(80, 1L)                   // data.l[3] = button 1
            event.setLong(88, 1L)                   // data.l[4] = source = normal application

            x11.XUngrabPointer(display, NativeLong(0))
            x11.XFlush(display)
            val mask = NativeLong((1L shl 20) or (1L shl 19)) // SubstructureRedirectMask | SubstructureNotifyMask
            x11.XSendEvent(display, rootWindow, false, mask, event)
            x11.XFlush(display)
        } finally {
            x11.XCloseDisplay(display)
        }
    }.isSuccess

    private fun tryWindows(window: Window): Boolean = runCatching {
        val hwndLong = Native.getComponentID(window)
        logger.debug("tryWindows: hwnd={}", hwndLong)
        val hwnd = WinDef.HWND(Pointer(hwndLong))
        Win32User32.INSTANCE.ReleaseCapture()
        User32.INSTANCE.PostMessage(
            hwnd,
            0xA1,               // WM_NCLBUTTONDOWN
            WinDef.WPARAM(2L),  // HTCAPTION
            WinDef.LPARAM(0L),
        )
    }.isSuccess
}

/**
 * Replaces WindowDraggableArea: on drag start, hands control to the OS window manager
 * via platform-native protocols (_NET_WM_MOVERESIZE on X11, WM_NCLBUTTONDOWN on Windows).
 * This gives native-speed drag and enables WM edge-snap zones.
 * Falls back to manual setLocation() if the native call fails (e.g., unsupported compositor).
 */
fun Modifier.nativeWindowDrag(window: Window): Modifier = pointerInput(window) {
    var nativeSucceeded = false
    detectDragGestures(
        onDragStart = { _ ->
            val pos = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
            nativeSucceeded = NativeWindowDrag.tryStart(window, pos.x, pos.y)
        },
        onDrag = { change, dragAmount ->
            if (!nativeSucceeded) {
                // Fallback for unsupported compositors (pure Wayland without XWayland, etc.)
                window.setLocation(
                    window.x + dragAmount.x.toInt(),
                    window.y + dragAmount.y.toInt(),
                )
            }
            change.consume()
        },
    )
}
