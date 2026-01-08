package com.franklinharper.battlezone

import androidx.compose.runtime.Composable

interface RecordingFilePicker {
    suspend fun saveRecording(bytes: ByteArray): Boolean
    suspend fun loadRecording(): ByteArray?
}

@Composable
expect fun rememberRecordingFilePicker(): RecordingFilePicker
