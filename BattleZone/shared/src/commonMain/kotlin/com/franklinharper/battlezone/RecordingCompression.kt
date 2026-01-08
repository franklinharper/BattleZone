package com.franklinharper.battlezone

import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer

object RecordingCompression {
    private val gzipMagic = byteArrayOf(0x1f, 0x8b.toByte())

    fun compressToBytes(json: String): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { sink ->
            sink.writeUtf8(json)
        }
        return buffer.readByteString().toByteArray()
    }

    fun decompressToJson(bytes: ByteArray): String {
        require(isGzip(bytes)) { "Recording is not gzip-compressed." }

        val buffer = Buffer().write(bytes)
        GzipSource(buffer).buffer().use { source ->
            return source.readUtf8()
        }
    }

    private fun isGzip(bytes: ByteArray): Boolean {
        if (bytes.size < gzipMagic.size) return false
        return bytes[0] == gzipMagic[0] && bytes[1] == gzipMagic[1]
    }
}
