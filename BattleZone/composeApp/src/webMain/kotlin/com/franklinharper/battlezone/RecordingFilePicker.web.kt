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

private const val DEFAULT_RECORDING_FILE_NAME = "battlezone-recording.json"

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
        anchor.download = DEFAULT_RECORDING_FILE_NAME
        anchor.click()
        window.URL.revokeObjectURL(url)
        return true
    }

    override suspend fun loadRecording(): String? = suspendCancellableCoroutine { continuation ->
        try {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "application/json,.json"
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
