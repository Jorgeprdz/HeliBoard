// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Trevor NK. Adapted from Ramblr commit 6538900746676382c2422a42a026fa154264f6a0.
package helium314.keyboard.latin.ai.dictation.ramblr

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe IDLE -> RECORDING -> TRANSCRIBING -> IDLE state machine shared between the UI
 * thread (taps, transcription callbacks) and the recording reader thread. Transitions are
 * compare-and-set so both threads agree on who "won" a given transition without a lock, and the
 * reader thread can safely detect "state moved away from RECORDING" without missing the update
 * (the previous plain `var state` was not visible across threads without synchronization).
 */
class RecordingStateMachine {
    enum class State { IDLE, RECORDING, TRANSCRIBING }

    private val ref = AtomicReference(State.IDLE)

    fun current(): State = ref.get()

    fun isRecording(): Boolean = ref.get() == State.RECORDING

    /** IDLE -> RECORDING. Returns false (no-op) if not currently IDLE. */
    fun tryStartRecording(): Boolean = ref.compareAndSet(State.IDLE, State.RECORDING)

    /** RECORDING -> TRANSCRIBING. Returns false (no-op) if not currently RECORDING. */
    fun tryStartTranscribing(): Boolean = ref.compareAndSet(State.RECORDING, State.TRANSCRIBING)

    /** Unconditionally forces IDLE, e.g. once transcription completes or on teardown. */
    fun reset() { ref.set(State.IDLE) }
}
