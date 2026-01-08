package com.franklinharper.battlezone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.js.Date

private const val RECORDING_FILE_PREFIX = "battlezone"

@Composable
actual fun rememberRecordingFilePicker(): RecordingFilePicker = remember {
    WebRecordingFilePicker()
}

private class WebRecordingFilePicker : RecordingFilePicker {
    override suspend fun saveRecording(json: String): Boolean {
        val blob = Blob(arrayOf(json), BlobPropertyBag(type = "application/json"))
        val url = window.URL.createObjectURL(blob)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = defaultRecordingFileName()
        anchor.click()
        window.URL.revokeObjectURL(url)
        return true
    }

    override suspend fun loadRecording(): String? = suspendCancellableCoroutine { continuation ->
        try {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
        input.accept = "application/json,.json,.bzr"
            input.onchange = {
                val file = input.files?.item(0)
                if (file == null) {
                    continuation.resume(null)
                } else {
                    val reader = FileReader()
                    reader.onloadend = {
                        continuation.resume(reader.result as? String)
                    }
                    reader.readAsText(file)
                }
            }
            input.click()
        } catch (ex: Throwable) {
            continuation.resumeWithException(ex)
        }
    }
}

private fun defaultRecordingFileName(): String {
    val date = Date()
    val year = date.getFullYear().toString().padStart(4, '0')
    val month = (date.getMonth() + 1).toString().padStart(2, '0')
    val day = date.getDate().toString().padStart(2, '0')
    val hour = date.getHours().toString().padStart(2, '0')
    val minute = date.getMinutes().toString().padStart(2, '0')
    return "$RECORDING_FILE_PREFIX-$year-$month-$day-$hour-$minute.bzr"
}
