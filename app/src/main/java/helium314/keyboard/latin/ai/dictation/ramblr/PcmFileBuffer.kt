// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Trevor NK. Adapted from Ramblr commit 6538900746676382c2422a42a026fa154264f6a0.
package helium314.keyboard.latin.ai.dictation.ramblr

import java.io.File
import java.io.FileOutputStream

/**
 * Streams PCM chunks straight to a temp file instead of an in-memory buffer, so heap stays flat
 * for the duration of a recording. [maxBytes] is a hard backstop independent of any duration
 * timer — `write` refuses once the cap would be exceeded instead of growing without bound.
 */
class PcmFileBuffer(private val file: File, private val maxBytes: Long) : AutoCloseable {
    private val out = FileOutputStream(file)

    var bytesWritten: Long = 0
        private set

    /**
     * Writes [len] bytes from [buf] starting at [offset]. Returns true if the whole chunk fit, or
     * false once the cap is reached. When only part of the chunk fits, that fitting prefix is still
     * written (floored to an even byte count so a 16-bit sample is never split) before returning
     * false — keeping the last complete 16-bit samples at the two-minute duration cap.
     */
    fun write(buf: ByteArray, offset: Int, len: Int): Boolean {
        if (bytesWritten + len > maxBytes) {
            val remaining = (maxBytes - bytesWritten).coerceAtLeast(0L).toInt()
            val fits = minOf(len, remaining).let { it - (it % 2) }
            if (fits > 0) {
                out.write(buf, offset, fits)
                bytesWritten += fits
            }
            return false
        }
        out.write(buf, offset, len)
        bytesWritten += len
        return true
    }

    override fun close() { out.close() }
}
