
package com.gestureai.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceCommandHelper {

    public interface Listener {
        void onCommand(String command);
        void onError(String error);
    }

    private SpeechRecognizer recognizer;

    public void start(Context context, Listener listener) {

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Microphone permission nahi hai");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Speech recognition available nahi hai");
            return;
        }

        stop();

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);

        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                listener.onError("Awaaz samajh nahi aayi. Dobara try karein.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                if (matches != null && !matches.isEmpty()) {
                    listener.onCommand(matches.get(0));
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "hi-IN"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "hi-IN"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
        );

        recognizer.startListening(intent);
    }

    public void stop() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }
}
