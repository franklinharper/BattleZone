package com.franklinharper.battlezone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerModeExportToService
import platform.UIKit.UIDocumentPickerModeImport
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.presentViewController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.usePinned
import platform.Foundation.NSURL
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private const val RECORDING_DOCUMENT_TYPE = "public.json"
private const val RECORDING_FILE_PREFIX = "battlezone"

@Composable
actual fun rememberRecordingFilePicker(): RecordingFilePicker = remember {
    IosRecordingFilePicker()
}

private class IosRecordingFilePicker : RecordingFilePicker {
    private var activeDelegate: RecordingDocumentPickerDelegate? = null

    override suspend fun saveRecording(json: String): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val tempUrl = createTempJsonFile(json)
            if (tempUrl == null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            val picker = UIDocumentPickerViewController(
                urls = listOf(tempUrl),
                inMode = UIDocumentPickerModeExportToService
            )

            activeDelegate = RecordingDocumentPickerDelegate(
                onPick = {
                    activeDelegate = null
                    continuation.resume(true)
                },
                onCancel = {
                    activeDelegate = null
                    continuation.resume(false)
                }
            )

            picker.delegate = activeDelegate
            presentPicker(picker)
        } catch (ex: Throwable) {
            continuation.resumeWithException(ex)
        }
    }

    override suspend fun loadRecording(): String? = suspendCancellableCoroutine { continuation ->
        try {
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf(RECORDING_DOCUMENT_TYPE),
                inMode = UIDocumentPickerModeImport
            )

            activeDelegate = RecordingDocumentPickerDelegate(
                onPick = { urls ->
                    activeDelegate = null
                    val firstUrl = urls.firstOrNull()
                    val data = firstUrl?.let { NSData.dataWithContentsOfURL(it) }
                    val text = data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
                    continuation.resume(text)
                },
                onCancel = {
                    activeDelegate = null
                    continuation.resume(null)
                }
            )

            picker.delegate = activeDelegate
            presentPicker(picker)
        } catch (ex: Throwable) {
            continuation.resumeWithException(ex)
        }
    }
}

private class RecordingDocumentPickerDelegate(
    private val onPick: (List<NSURL>) -> Unit,
    private val onCancel: () -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
        onPick(urls)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancel()
    }
}

private fun createTempJsonFile(json: String): NSURL? {
    val data = json.encodeToByteArray().toNSData()
    val fileName = "${defaultRecordingFileName()}"
    val directory = NSTemporaryDirectory()
    val url = NSURL.fileURLWithPath(directory).URLByAppendingPathComponent(fileName) ?: return null
    return if (data.writeToURL(url, true)) url else null
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun defaultRecordingFileName(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd-HH-mm"
    val timestamp = formatter.stringFromDate(NSDate())
    return "$RECORDING_FILE_PREFIX-$timestamp.bzr"
}

private fun presentPicker(picker: UIDocumentPickerViewController) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: UIApplication.sharedApplication.windows.firstOrNull { window: UIWindow -> window.isKeyWindow }?.rootViewController
    val presenter = root?.topmostViewController() ?: return
    presenter.presentViewController(picker, animated = true, completion = null)
}

private fun UIViewController.topmostViewController(): UIViewController {
    var current: UIViewController = this
    while (current.presentedViewController != null) {
        current = current.presentedViewController!!
    }
    return current
}
