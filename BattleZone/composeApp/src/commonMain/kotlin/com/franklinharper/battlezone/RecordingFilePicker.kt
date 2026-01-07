package com.franklinharper.battlezone

import androidx.compose.runtime.Composable

interface RecordingFilePicker {
    suspend fun saveRecording(json: String): Boolean
    suspend fun loadRecording(): String?
}

@Composable
expect fun rememberRecordingFilePicker(): RecordingFilePicker
