package com.omarchytv.home;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.List;

/**
 * v2.14: the in-app SpeechRecognizer for the companion chat and Ask, as in GrokbotDirectActivity. Through
 * RECOGNIZE_SPEECH, Google's recognizer on Android 11 (SHIELD, Katniss 7.55) logs "call for recognition service
 * without RECORD_AUDIO permissions" and does not record. Here OmarchyTV holds RECORD_AUDIO and calls the service.
 * The activity keeps its old recognizer intent as the fallback ({@link Listener#onFallback}).
 * The activity must forward onRequestPermissionsResult and call {@link #cancel} in onPause.
 */
final class VoiceInput {
    static final int REQ_MIC = 44;
    static final String NO_MATCH = "I didn't catch that. Try again or type.";
    static final String NO_PERMISSION = "The microphone permission is off for OmarchyTV.";

    interface Listener {
        /** Listening starts (partial is "") or a partial result comes in. */
        void onListening(String partial);

        /** Listening stops. Always comes before onText and onFail. */
        void onIdle();

        /** The recognized text, never empty. */
        void onText(String text);

        /** A short line for the user. */
        void onFail(String message);

        /** Use the system recognizer activity. */
        void onFallback();
    }

    private final Activity a;
    private final Listener l;
    private SpeechRecognizer recognizer;

    VoiceInput(Activity a, Listener l) {
        this.a = a;
        this.l = l;
    }

    boolean listening() {
        return recognizer != null;
    }

    /** Starts to listen. If it listens now, it stops (the result still comes). */
    void toggle() {
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (Exception e) {
                cancel();
            }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(a)) {
            l.onFallback();
            return;
        }
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        listen();
    }

    /** True when the result was for REQ_MIC. */
    boolean onPermissionResult(int req, int[] res) {
        if (req != REQ_MIC) return false;
        if (res != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) listen();
        else l.onFallback();   // no mic permission: the system recognizer may still work
        return true;
    }

    private void listen() {
        cancel();
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(a);
        } catch (Exception e) {
            recognizer = null;
            l.onFallback();
            return;
        }
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int type, Bundle p) { }

            @Override public void onPartialResults(Bundle r) {
                String s = first(r);
                if (recognizer != null && !s.isEmpty()) l.onListening(s);
            }

            @Override public void onResults(Bundle r) {
                String s = first(r);
                cancel();
                if (!s.isEmpty()) l.onText(s);
                else l.onFail(NO_MATCH);
            }

            @Override public void onError(int err) {
                cancel();
                l.onFail(message(err));
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, MainActivity.speechLanguage(a))
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, a.getPackageName());
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            cancel();
            l.onFallback();
            return;
        }
        l.onListening("");
    }

    /** Stops and destroys the recognizer. No result comes after this. */
    void cancel() {
        if (recognizer == null) return;
        try {
            recognizer.destroy();
        } catch (Exception ignored) {
        }
        recognizer = null;
        l.onIdle();
    }

    private static String first(Bundle r) {
        return r == null ? "" : first(r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
    }

    /** The first result, trimmed; "" when there is none. */
    static String first(List<String> r) {
        if (r == null) return "";
        for (String s : r) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    /** The line for a SpeechRecognizer error code. */
    static String message(int err) {
        if (err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return NO_MATCH;
        if (err == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return NO_PERMISSION;
        return "Voice error " + err + ". Try again or type.";
    }

    /** Partial text for a small label: at most max characters, with "…" when cut. */
    static String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : "…" + s.substring(s.length() - (max - 1));
    }
}
