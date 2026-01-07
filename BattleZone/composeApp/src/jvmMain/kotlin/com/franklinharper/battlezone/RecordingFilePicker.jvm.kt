package com.franklinharper.battlezone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private const val DEFAULT_RECORDING_FILE_NAME = "battlezone-recording.json"

@Composable
actual fun rememberRecordingFilePicker(): RecordingFilePicker = remember {
    JvmRecordingFilePicker()
}

private class JvmRecordingFilePicker : RecordingFilePicker {
    override suspend fun saveRecording(json: String): Boolean = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Save Recording", FileDialog.SAVE)
        dialog.file = DEFAULT_RECORDING_FILE_NAME
        dialog.isVisible = true

        val fileName = dialog.file ?: return@withContext false
        val directory = dialog.directory ?: return@withContext false
        val file = File(directory, fileName)
        withContext(Dispatchers.IO) {
            file.writeText(json)
        }
        true
    }

    override suspend fun loadRecording(): String? = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Open Recording", FileDialog.LOAD)
        dialog.isVisible = true

        val fileName = dialog.file ?: return@withContext null
        val directory = dialog.directory ?: return@withContext null
        val file = File(directory, fileName)
        withContext(Dispatchers.IO) {
            file.takeIf { it.exists() }?.readText()
        }
    }
}
