
package com.gestureai.demo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceForegroundService extends Service {

    public static final String ACTION_START =
            "com.gestureai.demo.VOICE_START";

    public static final String ACTION_STOP =
            "com.gestureai.demo.VOICE_STOP";

    private static final String CHANNEL_ID =
            "gesture_voice_channel";

    private static final int NOTIFICATION_ID = 501;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;

    private boolean serviceRunning = false;
    private boolean listening = false;
    private boolean destroyed = false;
    private boolean foregroundStarted = false;

    private int retryCount = 0;
    private int generation = 0;

    private static final long MIN_RETRY_DELAY = 1500;
    private static final long MAX_RETRY_DELAY = 8000;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null
                && ACTION_STOP.equals(intent.getAction())) {

            stopVoice();
            stopSelf();
            return START_NOT_STICKY;
        }

        startVoice();
        return START_STICKY;
    }

    private void startVoice() {

        if (destroyed || serviceRunning) {
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            stopSelf();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }

        try {
            startVoiceForeground();

            serviceRunning = true;
            retryCount = 0;

            startListening();

        } catch (Exception e) {

            serviceRunning = false;
            stopForeground(true);
            stopSelf();
        }
    }

    private void startVoiceForeground() {

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }

        foregroundStarted = true;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Gesture AI Voice Control",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Voice recognition is active"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {

        Intent stopIntent = new Intent(
                this,
                VoiceForegroundService.class
        );

        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent =
                PendingIntent.getService(
                        this,
                        502,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Gesture AI Voice Control")
                .setContentText("Voice commands सुन रहा है")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        "बंद करें",
                        stopPendingIntent
                )
                .build();
    }

    private void startListening() {

        if (!serviceRunning || destroyed || listening) {
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            stopVoice();
            stopSelf();
            return;
        }

        try {

            destroyRecognizer();

            final int currentGeneration = ++generation;

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(
                    new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {

                    if (currentGeneration != generation) {
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

                    if (currentGeneration != generation) {
                        return;
                    }

                    listening = false;
                }

                @Override
                public void onError(int error) {

                    if (currentGeneration != generation) {
                        return;
                    }

                    listening = false;

                    if (!serviceRunning || destroyed) {
                        return;
                    }

                    scheduleRestart(error);
                }

                @Override
                public void onResults(Bundle results) {

                    if (currentGeneration != generation) {
                        return;
                    }

                    listening = false;

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {

                        String command = matches.get(0);

                        if (command != null
                                && !command.trim().isEmpty()) {

                            handleVoiceCommand(command.trim());
                        }
                    }

                    if (serviceRunning && !destroyed) {

                        retryCount = 0;

                        handler.postDelayed(
                                () -> startListening(),
                                500
                        );
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent recognitionIntent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
            );

            speechRecognizer.startListening(recognitionIntent);

        } catch (Exception e) {

            listening = false;
            scheduleRestart(-1);
        }
    }

    private void handleVoiceCommand(String command) {

        if (command == null || command.trim().isEmpty()) {
            return;
        }

        String normalized = command
                .toLowerCase(Locale.ROOT)
                .trim();

        if (isStopCommand(normalized)) {

            stopVoice();
            stopSelf();
            return;
        }

        try {

            Intent intent = new Intent(
                    this,
                    FloatingOverlayService.class
            );

            intent.setAction(
                    "com.gestureai.demo.VOICE_COMMAND"
            );

            intent.putExtra(
                    "voice_command",
                    command
            );

            startService(intent);

        } catch (Exception e) {
            // Recognition continues if command delivery fails.
        }
    }

    private boolean isStopCommand(String command) {

        if (command == null) {
            return false;
        }

        String text = command.toLowerCase(Locale.ROOT).trim();

        return text.equals("stop")
                || text.equals("stop listening")
                || text.equals("stop voice")
                || text.equals("stop voice control")
                || text.equals("gesture ai stop")
                || text.equals("बंद करो")
                || text.equals("बंद कर दो")
                || text.equals("आवाज बंद करो")
                || text.equals("वॉइस बंद करो")
                || text.equals("वॉइस कंट्रोल बंद करो")
                || text.equals("जेस्चर ए आई बंद करो");
    }

    private void scheduleRestart(int error) {

        if (!serviceRunning || destroyed) {
            return;
        }

        handler.removeCallbacksAndMessages(null);

        listening = false;

        destroyRecognizer();

        retryCount++;

        long delay = Math.min(
                MIN_RETRY_DELAY * retryCount,
                MAX_RETRY_DELAY
        );

        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            delay = MAX_RETRY_DELAY;
        }

        handler.postDelayed(
                () -> {

                    if (serviceRunning && !destroyed) {
                        startListening();
                    }
                },
                delay
        );
    }

    private void destroyRecognizer() {

        generation++;

        if (speechRecognizer != null) {

            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }

            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {
            }

            speechRecognizer = null;
        }

        listening = false;
    }

    private void stopVoice() {

        serviceRunning = false;
        listening = false;
        destroyed = true;

        handler.removeCallbacksAndMessages(null);

        destroyRecognizer();

        if (foregroundStarted) {

            stopForeground(true);
            foregroundStarted = false;
        }
    }

    @Override
    public void onDestroy() {

        stopVoice();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
