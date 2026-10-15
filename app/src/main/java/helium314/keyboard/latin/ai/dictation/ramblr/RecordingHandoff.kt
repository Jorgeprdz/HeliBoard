// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Trevor NK. Adapted from Ramblr commit 6538900746676382c2422a42a026fa154264f6a0.
package helium314.keyboard.latin.ai.dictation.ramblr

/**
 * Pure handoff decision. A superseded reader may never claim the new recording's transition and
 * must discard its own audio.
 */
object RecorderHandoff {
    /** Whether the reader may claim RECORDING -> TRANSCRIBING. Only a still-current reader may. */
    fun shouldClaimTranscribing(superseded: Boolean): Boolean = !superseded

    /** Whether this reader's audio must be discarded (never handed to transcription). */
    fun discarded(superseded: Boolean, stateAfterClaim: RecordingStateMachine.State): Boolean =
        superseded || stateAfterClaim != RecordingStateMachine.State.TRANSCRIBING
}
