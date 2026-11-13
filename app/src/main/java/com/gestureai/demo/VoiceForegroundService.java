
package com.gestureai.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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

    private static final String CHANNEL_ID = "gesture_voice_channel";
    private static final int NOTIFICATION_ID = 501;

    private SpeechRecognizer recognizer;
    private Handler handler;
    private boolean running = false;
    private boolean listening = false;

    private final Runnable restartListener = () -> {
        if (running) {
            startListening();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVoice();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            startVoice();
        }

        return START_STICKY;
    }

    private void startVoice() {

        if (running) return;

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }

        running = true;

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startListening();
    }

    private void startListening() {

        if (!running || listening) return;

        try {
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(this);

            recognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
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
                    listening = false;
                }

                @Override
                public void onError(int error) {
                    listening = false;
                    scheduleRestart();
                }

                @Override
                public void onResults(Bundle results) {

                    listening = false;

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {

                        String command = matches.get(0);

                        if (isStopCommand(command)) {
                            stopVoice();
                            return;
                        }

                        sendCommand(command);
                    }

                    scheduleRestart();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent speechIntent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
            );

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
            );

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            speechIntent.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
            );

            recognizer.startListening(speechIntent);

        } catch (Exception e) {
            listening = false;
            scheduleRestart();
        }
    }

    private void scheduleRestart() {
        if (running && handler != null) {
            handler.removeCallbacks(restartListener);
            handler.postDelayed(restartListener, 900);
        }
    }

    private boolean isStopCommand(String command) {

        if (command == null) return false;

        String text = command.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0900-\\u097F]", "");

        return text.contains("gestureaiband")
                || text.contains("voicecontrolband")
                || text.contains("stoplistening")
                || text.contains("बंदहोजाओ")
                || text.contains("सुननाबंदकरो");
    }

    private void sendCommand(String command) {

        Intent intent = new Intent(
                this,
                FloatingOverlayService.class
        );

        intent.setAction("com.gestureai.demo.VOICE_COMMAND");
        intent.putExtra("voice_command", command);

        try {
            startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Gesture AI Voice Control",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Voice control is active"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Gesture AI Voice Control")
                .setContentText("Voice control चालू है")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void stopVoice() {

        running = false;
        listening = false;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {

        running = false;
        listening = false;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }
