// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Trevor NK. Adapted from Ramblr commit 6538900746676382c2422a42a026fa154264f6a0.
package helium314.keyboard.latin.ai.dictation.ramblr

/** Pure elapsed-time check for the recording max-duration cap, extracted so it's unit-testable. */
object RecordingDurationCap {
    fun exceeded(startedAtMs: Long, nowMs: Long, maxDurationMs: Long): Boolean =
        nowMs - startedAtMs >= maxDurationMs
}
