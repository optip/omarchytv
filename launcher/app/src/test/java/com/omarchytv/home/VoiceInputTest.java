package com.omarchytv.home;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** v2.14: the pure parts of the in-app voice input (companion chat and Ask). */
public class VoiceInputTest {
    // android.speech.SpeechRecognizer error codes (compile-time constants, so the stub jar is not called).
    private static final int ERROR_SPEECH_TIMEOUT = 6;
    private static final int ERROR_NO_MATCH = 7;
    private static final int ERROR_INSUFFICIENT_PERMISSIONS = 9;

    @Test
    public void noMatchAndTimeoutAskToTryAgain() {
        assertEquals(VoiceInput.NO_MATCH, VoiceInput.message(ERROR_NO_MATCH));
        assertEquals(VoiceInput.NO_MATCH, VoiceInput.message(ERROR_SPEECH_TIMEOUT));
        assertEquals("I didn't catch that. Try again or type.", VoiceInput.NO_MATCH);
    }

    @Test
    public void missingPermissionSaysSo() {
        assertEquals(VoiceInput.NO_PERMISSION, VoiceInput.message(ERROR_INSUFFICIENT_PERMISSIONS));
    }

    @Test
    public void otherErrorsShowTheCode() {
        assertEquals("Voice error 2. Try again or type.", VoiceInput.message(2));
        assertEquals("Voice error 8. Try again or type.", VoiceInput.message(8));
    }

    @Test
    public void firstNonEmptyTrimmedResult() {
        assertEquals("open YouTube", VoiceInput.first(Arrays.asList("  open YouTube ", "open you tube")));
        assertEquals("apri Netflix", VoiceInput.first(Arrays.asList(null, "   ", "apri Netflix")));
    }

    @Test
    public void noResultIsEmpty() {
        assertEquals("", VoiceInput.first(null));
        assertEquals("", VoiceInput.first(Collections.emptyList()));
        assertEquals("", VoiceInput.first(Arrays.asList(null, "", " ")));
    }

    @Test
    public void clipKeepsTheLastWords() {
        assertEquals("open YouTube", VoiceInput.clip(" open YouTube ", 24));
        String c = VoiceInput.clip("please open the YouTube app on the TV", 10);
        assertEquals(10, c.length());
        assertEquals("…on the TV", c);
        assertEquals("", VoiceInput.clip(null, 10));
    }
}
