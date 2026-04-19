package org.example.project.ui.diagnostics

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.net.URI
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.ObjBase
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

internal fun selectJsonFileForSeedImport(): File? {
    val dialog = FileDialog(null as Frame?, "Seleziona file JSON seed applicazione", FileDialog.LOAD).apply {
        directory = File(System.getProperty("user.home") ?: ".").absolutePath
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val selectedName = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    val selected = File(selectedDirectory, selectedName)
    if (!selected.isFile) return null
    if (!selected.name.endsWith(".json", ignoreCase = true)) return null
    return selected
}

internal fun selectDirectoryForDatabaseMove(initialDirectory: File): File? {
    if (!isWindows()) return null
    val selected = showModernWindowsFolderPicker(initialDirectory)
    return selected
}

private fun isWindows(): Boolean =
    (System.getProperty("os.name") ?: "").contains("windows", ignoreCase = true)

private fun showModernWindowsFolderPicker(initialDirectory: File): File? {
    val initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
    val shouldUninitialize = initHr.toInt() == COMUtils.S_OK || initHr.toInt() == COMUtils.S_FALSE
    if (COMUtils.FAILED(initHr) && initHr.toInt() != W32Errors.RPC_E_CHANGED_MODE) {
        COMUtils.checkRC(initHr)
    }

    try {
        val dialog = createFileOpenDialog()
        try {
            val options = dialog.getOptions()
            dialog.setOptions(options or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST)
            dialog.setTitle("Seleziona la cartella in cui salvare il database")
            dialog.setOkButtonLabel("Seleziona")

            createShellItem(initialDirectory)?.use { folder ->
                dialog.setDefaultFolder(folder)
                dialog.setFolder(folder)
            }

            val owner = User32.INSTANCE.GetForegroundWindow()
            val showHr = dialog.show(owner)
            if (showHr.toInt() == HRESULT_ERROR_CANCELLED) {
                return null
            }
            COMUtils.checkRC(showHr)

            dialog.getResult().use { result ->
                val selectedPath = result.getFileSystemPath() ?: return null
                return parseSelectedDirectory(selectedPath)
            }
        } finally {
            dialog.Release()
        }
    } finally {
        if (shouldUninitialize) {
            Ole32.INSTANCE.CoUninitialize()
        }
    }
}

private fun parseSelectedDirectory(rawPath: String): File {
    val normalizedRawPath = rawPath.trim()
    val directFile = File(normalizedRawPath)
    if (directFile.isDirectory) return directFile

    if (normalizedRawPath.startsWith("file:/", ignoreCase = true)) {
        val uriFile = runCatching { File(URI(normalizedRawPath)) }.getOrNull()
        if (uriFile?.isDirectory == true) return uriFile
    }

    throw IOException("Cartella selezionata non valida. Valore restituito dal picker: $normalizedRawPath")
}

private fun createFileOpenDialog(): FileOpenDialog {
    val reference = PointerByReference()
    val hr = Ole32.INSTANCE.CoCreateInstance(
        CLSID_FILE_OPEN_DIALOG,
        Pointer.NULL,
        ObjBase.CLSCTX_INPROC,
        IID_I_FILE_OPEN_DIALOG,
        reference,
    )
    COMUtils.checkRC(hr)
    return FileOpenDialog(reference.value)
}

private fun createShellItem(directory: File): ShellItem? {
    if (!directory.isDirectory) return null
    val reference = PointerByReference()
    val hr = Shell32Extra.INSTANCE.SHCreateItemFromParsingName(
        WString(directory.absolutePath),
        Pointer.NULL,
        Guid.REFIID(IID_I_SHELL_ITEM),
        reference,
    )
    COMUtils.checkRC(hr)
    return ShellItem(reference.value)
}

internal fun selectDatabaseFile(initialDirectory: File): File? {
    val dialog = FileDialog(null as Frame?, "Seleziona file database da usare come sorgente dati", FileDialog.LOAD).apply {
        directory = initialDirectory.absolutePath
        filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".sqlite", ignoreCase = true) || name.endsWith(".db", ignoreCase = true)
        }
        isVisible = true
    }
    val selectedName = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    val selected = File(selectedDirectory, selectedName)
    return selected.takeIf { it.isFile }
}

private interface Shell32Extra : StdCallLibrary {
    companion object {
        val INSTANCE: Shell32Extra = Native.load("shell32", Shell32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    fun SHCreateItemFromParsingName(
        pszPath: WString,
        pbc: Pointer?,
        riid: Guid.REFIID,
        ppv: PointerByReference,
    ): WinNT.HRESULT
}

private class FileOpenDialog(pointer: Pointer) : Unknown(pointer) {
    fun show(owner: WinDef.HWND?): WinNT.HRESULT =
        _invokeNativeObject(3, arrayOf(pointer, owner), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun setOptions(options: Int) {
        COMUtils.checkRC(_invokeNativeObject(9, arrayOf(pointer, options), WinNT.HRESULT::class.java) as WinNT.HRESULT)
    }

    fun getOptions(): Int {
        val options = IntByReference()
        COMUtils.checkRC(_invokeNativeObject(10, arrayOf(pointer, options), WinNT.HRESULT::class.java) as WinNT.HRESULT)
        return options.value
    }

    fun setDefaultFolder(folder: ShellItem) {
        COMUtils.checkRC(_invokeNativeObject(11, arrayOf(pointer, folder.pointer), WinNT.HRESULT::class.java) as WinNT.HRESULT)
    }

    fun setFolder(folder: ShellItem) {
        COMUtils.checkRC(_invokeNativeObject(12, arrayOf(pointer, folder.pointer), WinNT.HRESULT::class.java) as WinNT.HRESULT)
    }

    fun setTitle(title: String) {
        COMUtils.checkRC(_invokeNativeObject(17, arrayOf(pointer, WString(title)), WinNT.HRESULT::class.java) as WinNT.HRESULT)
    }

    fun setOkButtonLabel(label: String) {
        COMUtils.checkRC(_invokeNativeObject(18, arrayOf(pointer, WString(label)), WinNT.HRESULT::class.java) as WinNT.HRESULT)
    }

    fun getResult(): ShellItem {
        val result = PointerByReference()
        COMUtils.checkRC(_invokeNativeObject(20, arrayOf(pointer, result), WinNT.HRESULT::class.java) as WinNT.HRESULT)
        return ShellItem(result.value)
    }
}

private class ShellItem(pointer: Pointer) : Unknown(pointer), AutoCloseable {
    fun getFileSystemPath(): String? {
        getDisplayName(SIGDN_FILESYSPATH)?.takeIf { it.isNotBlank() }?.let { return it }
        return getDisplayName(SIGDN_DESKTOPABSOLUTEPARSING)?.takeIf { it.isNotBlank() }
    }

    private fun getDisplayName(sigdn: Int): String? {
        val result = PointerByReference()
        COMUtils.checkRC(_invokeNativeObject(5, arrayOf(pointer, sigdn, result), WinNT.HRESULT::class.java) as WinNT.HRESULT)
        val pathPointer = result.value ?: return null
        return try {
            pathPointer.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pathPointer)
        }
    }

    override fun close() {
        Release()
    }
}

private val CLSID_FILE_OPEN_DIALOG = Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
private val IID_I_FILE_OPEN_DIALOG = Guid.IID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
private val IID_I_SHELL_ITEM = Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")

private const val FOS_PICKFOLDERS = 0x00000020
private const val FOS_FORCEFILESYSTEM = 0x00000040
private const val FOS_PATHMUSTEXIST = 0x00000800
private const val SIGDN_FILESYSPATH = -2147123200
private const val SIGDN_DESKTOPABSOLUTEPARSING = -2147319808
private const val HRESULT_ERROR_CANCELLED = -2147023673
