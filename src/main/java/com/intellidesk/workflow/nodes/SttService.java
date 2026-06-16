package com.intellidesk.workflow.nodes;

/**
 * Interface for Speech-to-Text transcription services.
 * Implementations may call Deepgram, Whisper, or return mock transcripts.
 */
public interface SttService {

    /**
     * Transcribes audio bytes into text.
     *
     * @param audio the raw audio data (WAV, WebM, MP3, or OGG)
     * @return the transcribed text
     */
    String transcribe(byte[] audio);
}
