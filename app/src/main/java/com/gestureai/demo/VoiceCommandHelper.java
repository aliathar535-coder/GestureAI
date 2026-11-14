
package com.gestureai.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceCommandHelper {

    public interface Listener {
        void onCommand(String command);

        void onError(String message);
    }

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;

    private WeakReference<Context> contextReference;

    private Listener listener;

    private boolean listening = false;
    private boolean stopped = true;

    private int generation = 0;

    private String language = "hi-IN";

    public VoiceCommandHelper() {
    }

    public void start(Context context, Listener callback) {

        if (context == null || callback == null) {
            return;
        }

        runOnMain(() -> {

            stopInternal();

            Context appContext = context.getApplicationContext();

            contextReference = new WeakReference<>(appContext);
            listener = callback;
            stopped = false;

            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                sendError("Microphone Permission नहीं मिली");
                stopInternal();
                return;
            }

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {

                sendError("इस फोन में Speech Recognition उपलब्ध नहीं है");
                stopInternal();
                return;
            }

            createRecognizer();
        });
    }

    public void setLanguage(String languageCode) {

        if (languageCode == null || languageCode.trim().isEmpty()) {
            return;
        }

        runOnMain(() -> {

            language = languageCode.trim();

            if (!stopped && contextReference != null) {

                Context context = contextReference.get();

                if (context != null) {
                    restartWithNewLanguage(context);
                }
            }
        });
    }

    private void restartWithNewLanguage(Context context) {

        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();

        stopped = false;

        createRecognizer();
    }

    private void createRecognizer() {

        if (stopped || contextReference == null) {
            return;
        }

        Context context = contextReference.get();

        if (context == null) {
            stopInternal();
            return;
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            sendError("Microphone Permission नहीं मिली");
            stopInternal();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            sendError("Speech Recognition उपलब्ध नहीं है");
            stopInternal();
            return;
        }

        try {

            destroyRecognizer();

            final int currentGeneration = ++generation;

            recognizer =
                    SpeechRecognizer.createSpeechRecognizer(context);

            recognizer.setRecognitionListener(
                    new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {

                    if (!isCurrent(currentGeneration)) {
                        return;
                    }

                    listening = true;
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

                    if (!isCurrent(currentGeneration)) {
                        return;
                    }

                    listening = false;
                }

                @Override
                public void onError(int error) {

                    if (!isCurrent(currentGeneration)) {
                        return;
                    }

                    listening = false;

                    String message = getErrorMessage(error);

                    sendError(message);

                    stopInternal();
                }

                @Override
                public void onResults(Bundle results) {

                    if (!isCurrent(currentGeneration)) {
                        return;
                    }

                    listening = false;

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    String command = null;

                    if (matches != null && !matches.isEmpty()) {
                        command = matches.get(0);
                    }

                    if (command != null && !command.trim().isEmpty()) {

                        Listener currentListener = listener;

                        stopInternal();

                        if (currentListener != null) {
                            currentListener.onCommand(command.trim());
                        }

                    } else {

                        sendError("आवाज समझ नहीं आई। दोबारा बोलें");
                        stopInternal();
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
                    language
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    language
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
            );

            recognizer.startListening(intent);

        } catch (Exception e) {

            sendError("Voice Recognition शुरू नहीं हो पाया");
            stopInternal();
        }
    }

    private boolean isCurrent(int callbackGeneration) {

        return !stopped
                && callbackGeneration == generation
                && recognizer != null;
    }

    private String getErrorMessage(int error) {

        switch (error) {

            case SpeechRecognizer.ERROR_AUDIO:
                return "Microphone में समस्या है";

            case SpeechRecognizer.ERROR_CLIENT:
                return "Voice Recognition में समस्या हुई";

            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone Permission नहीं मिली";

            case SpeechRecognizer.ERROR_NETWORK:
                return "Internet या Network में समस्या है";

            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network Timeout हुआ";

            case SpeechRecognizer.ERROR_NO_MATCH:
                return "आवाज समझ नहीं आई";

            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice Recognition अभी व्यस्त है";

            case SpeechRecognizer.ERROR_SERVER:
                return "Speech Recognition Server में समस्या है";

            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "कोई आवाज नहीं मिली";

            default:
                return "Voice Recognition Error: " + error;
        }
    }

    private void sendError(String message) {

        Listener currentListener = listener;

        if (currentListener != null) {
            currentListener.onError(message);
        }
    }

    public void stop() {

        runOnMain(this::stopInternal);
    }

    private void stopInternal() {

        stopped = true;
        listening = false;

        handler.removeCallbacksAndMessages(null);

        destroyRecognizer();

        listener = null;
        contextReference = null;
    }

    private void destroyRecognizer() {

        generation++;

        if (recognizer != null) {

            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }

            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }

            recognizer = null;
        }

        listening = false;
    }

    public boolean isListening() {
        return listening && !stopped;
    }

    private void runOnMain(Runnable runnable) {

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }
}
