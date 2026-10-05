package dev.lumbina.tv.speech.vosk;

import dev.lumbina.tv.speech.vosk.IVoiceSession;

/** The recognizer in its own process (VoiceService). */
interface IVoiceEngine {
    /**
     * Keeps exactly the models of [load] loaded (loading the missing ones, which takes seconds)
     * and opens a session for [languages], some of [load] in order of preference.
     */
    IVoiceSession newSession(in List<String> load, in List<String> languages);
}
