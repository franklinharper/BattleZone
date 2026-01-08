package com.franklinharper.battlezone

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECORDING_MIME_TYPE = "application/json"
private const val RECORDING_FILE_PREFIX = "battlezone"

@Composable
actual fun rememberRecordingFilePicker(): RecordingFilePicker {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)

    var pendingSave by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var pendingLoad by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RECORDING_MIME_TYPE)
    ) { uri ->
        pendingSave?.complete(uri)
        pendingSave = null
    }

    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingLoad?.complete(uri)
        pendingLoad = null
    }

    return remember {
        AndroidRecordingFilePicker(
            contextProvider = { currentContext },
            launchSave = { deferred ->
                pendingSave = deferred
                saveLauncher.launch(defaultRecordingFileName())
            },
            launchLoad = { deferred ->
                pendingLoad = deferred
                loadLauncher.launch(arrayOf(RECORDING_MIME_TYPE))
            }
        )
    }
}

private class AndroidRecordingFilePicker(
    private val contextProvider: () -> Context,
    private val launchSave: (CompletableDeferred<Uri?>) -> Unit,
    private val launchLoad: (CompletableDeferred<Uri?>) -> Unit
) : RecordingFilePicker {
    override suspend fun saveRecording(json: String): Boolean {
        val deferred = CompletableDeferred<Uri?>()
        launchSave(deferred)
        val uri = deferred.await() ?: return false
        return withContext(Dispatchers.IO) {
            val context = contextProvider()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.encodeToByteArray())
            } != null
        }
    }

    override suspend fun loadRecording(): String? {
        val deferred = CompletableDeferred<Uri?>()
        launchLoad(deferred)
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val context = contextProvider()
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            }
        }
    }
}

private fun defaultRecordingFileName(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)
    val timestamp = formatter.format(Date())
    return "$RECORDING_FILE_PREFIX-$timestamp.bzr"
}
