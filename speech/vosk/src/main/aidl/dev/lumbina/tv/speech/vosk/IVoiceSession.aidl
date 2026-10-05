package dev.lumbina.tv.speech.vosk;

import android.os.Bundle;

/** A SpeechSession across processes; finish() returns the hypotheses as parallel arrays. */
interface IVoiceSession {
    void accept(in byte[] pcm, int length);

    String partial();

    Bundle finish();

    void close();
}
