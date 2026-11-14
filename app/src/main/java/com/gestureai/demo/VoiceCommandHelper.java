
package com.gestureai.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running = false;
    private boolean destroyed = false;

    private Context appContext;
    private Listener currentListener;

    private String language = "hi-IN";

    private final Runnable restartTask = () -> {
        if (running && !destroyed) {
            startListening();
        }
    };

    public void start(Context context, Listener listener) {

        if (context == null || listener == null) {
            return;
        }

        stop();

        appContext = context.getApplicationContext();
        currentListener = listener;
        running = true;
        destroyed = false;

        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            running = false;
            listener.onError("Microphone permission nahi hai");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {

            running = false;
            listener.onError(
                    "Speech recognition available nahi hai"
            );
            return;
        }

        startListening();
    }

    public void setLanguage(String languageCode) {

        if ("en-IN".equalsIgnoreCase(languageCode)) {
            language = "en-IN";
        } else {
            language = "hi-IN";
        }
    }

    private void startListening() {

        if (!running || destroyed || appContext == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            running = false;

            if (currentListener != null) {
                currentListener.onError(
                        "Microphone permission nahi hai"
                );
            }

            return;
        }

        try {

            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }

            recognizer =
                    SpeechRecognizer.createSpeechRecognizer(appContext);

            recognizer.setRecognitionListener(
                    new RecognitionListener() {

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

                    if (!running || destroyed) {
                        return;
                    }

                    scheduleRestart();

                    if (currentListener != null) {
                        currentListener.onError(
                                "Awaaz samajh nahi aayi. Dobara sun raha hoon."
                        );
                    }
                }

                @Override
                public void onResults(Bundle results) {

                    if (!running || destroyed) {
                        return;
                    }

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {

                        if (currentListener != null) {
                            currentListener.onCommand(matches.get(0));
                        }
                    }

                    scheduleRestart();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(
                        int eventType,
                        Bundle params
                ) {
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
                    language
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    language
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
            );

            recognizer.startListening(intent);

        } catch (Exception e) {

            scheduleRestart();

            if (currentListener != null) {
                currentListener.onError(
                        "Voice start nahi ho paya"
                );
            }
        }
    }

    private void scheduleRestart() {

        handler.removeCallbacks(restartTask);

        if (running && !destroyed) {
            handler.postDelayed(restartTask, 800);
        }
    }

    public void stop() {

        running = false;
        handler.removeCallbacks(restartTask);

        if (recognizer != null) {

            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) {
            }

            recognizer = null;
        }
    }

    public void destroy() {

        stop();

        destroyed = true;
        currentListener = null;
        appContext = null;
    }
}
