package com.franklinharper.battlezone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val RECORDING_FILE_PREFIX = "battlezone"

@Composable
actual fun rememberRecordingFilePicker(): RecordingFilePicker = remember {
    JvmRecordingFilePicker()
}

private class JvmRecordingFilePicker : RecordingFilePicker {
    override suspend fun saveRecording(bytes: ByteArray): Boolean = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Save Recording", FileDialog.SAVE)
        dialog.file = defaultRecordingFileName()
        dialog.isVisible = true

        val fileName = dialog.file ?: return@withContext false
        val directory = dialog.directory ?: return@withContext false
        val file = File(directory, fileName)
        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
        }
        true
    }

    override suspend fun loadRecording(): ByteArray? = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Open Recording", FileDialog.LOAD)
        dialog.isVisible = true

        val fileName = dialog.file ?: return@withContext null
        val directory = dialog.directory ?: return@withContext null
        val file = File(directory, fileName)
        withContext(Dispatchers.IO) {
            file.takeIf { it.exists() }?.readBytes()
        }
    }
}

private fun defaultRecordingFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")
    val timestamp = LocalDateTime.now().format(formatter)
    return "$RECORDING_FILE_PREFIX-$timestamp.bzr"
}
