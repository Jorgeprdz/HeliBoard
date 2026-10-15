Source: https://github.com/trevornk/ramblr
Commit: 6538900746676382c2422a42a026fa154264f6a0

Ported recording state, recorder ownership/handoff, bounded PCM file buffering and WAV serialization. Package changed; recording cap shortened to two minutes; elapsedRealtime replaces wall clock. No Ramblr Accessibility, floating overlay, second IME, model downloader or other service is imported. Android on-device SpeechRecognizer is a separate backend. Capture/transcription integration and hardware validation are tracked separately.
