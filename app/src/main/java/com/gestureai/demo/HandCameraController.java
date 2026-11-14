
package com.gestureai.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandCameraController {

    public interface Listener {
        void onLearningProgress(int count);
        void onGestureRecognized(String name, String action);
        void onError(String message);
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final Listener listener;

    private final HandGestureEngine engine;
    private final ExecutorService cameraExecutor;
    private final Handler mainHandler;

    private ProcessCameraProvider cameraProvider;

    private volatile boolean learning = false;
    private volatile boolean running = false;
    private volatile boolean closed = false;

    private String lastGesture = "";
    private long lastGestureTime = 0;

    public HandCameraController(
            Context context,
            LifecycleOwner lifecycleOwner,
            PreviewView previewView,
            Listener listener) {

        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.listener = listener;

        engine = new HandGestureEngine(context);

        cameraExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {

        if (running || closed) return;

        cameraExecutor.execute(() -> {

            try {
                engine.initialize();

                ListenableFuture<ProcessCameraProvider> future =
                        ProcessCameraProvider.getInstance(context);

                future.addListener(() -> {

                    if (closed) return;

                    try {
                        cameraProvider = future.get();
                        bindCamera();

                    } catch (Exception e) {
                        sendError("Camera start failed: "
                                + e.getMessage());
                    }

                }, ContextCompat.getMainExecutor(context));

            } catch (Exception e) {
                sendError("Hand recognition start failed: "
                        + e.getMessage());
            }
        });
    }

    private void bindCamera() {

        if (closed) return;

        Preview preview = new Preview.Builder().build();

        preview.setSurfaceProvider(
                previewView.getSurfaceProvider());

        ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        analysis.setAnalyzer(
                cameraExecutor,
                this::analyzeFrame);

        try {

            cameraProvider.unbindAll();

            cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
            );

            running = true;

        } catch (Exception e) {
            sendError("Camera binding failed: "
                    + e.getMessage());
        }
    }

    private void analyzeFrame(ImageProxy image) {

        try {

            if (closed) return;

            Bitmap bitmap = image.toBitmap();

            if (bitmap == null) return;

            if (learning) {

                int count = engine.addLearningFrame(bitmap);

                if (count >= 0) {

                    if (count >= 5) {
                        learning = false;
                    }

                    int progress = count;

                    mainHandler.post(() -> {
                        if (!closed && listener != null) {
                            listener.onLearningProgress(progress);
                        }
                    });
                }

            } else {

                JSONObject result = engine.recognize(bitmap);

                if (result != null) {

                    String name = result.optString("name");
                    String action = result.optString("action");

                    long now = System.currentTimeMillis();

                    if (!name.equals(lastGesture)
                            || now - lastGestureTime > 1500) {

                        lastGesture = name;
                        lastGestureTime = now;

                        mainHandler.post(() -> {
                            if (!closed && listener != null) {
                                listener.onGestureRecognized(
                                        name, action);
                            }
                        });
                    }
                }
            }

            bitmap.recycle();

        } catch (Exception e) {
            sendError("Frame processing failed: "
                    + e.getMessage());

        } finally {
            image.close();
        }
    }

    public boolean startLearning(String name, String action) {

        if (!running || learning || closed) {
            return false;
        }

        boolean started = engine.startLearning(name, action);

        if (started) {
            learning = true;
            lastGesture = "";
            lastGestureTime = 0;
        }

        return started;
    }

    public void cancelLearning() {

        learning = false;

        if (!closed) {
            engine.cancelLearning();
        }
    }

    public boolean removeGesture(String name) {

        if (closed) return false;

        return engine.removeGesture(name);
    }

    public JSONArray getSavedGestures() {

        if (closed) {
            return new JSONArray();
        }

        return engine.getSavedData();
    }

    private void sendError(String message) {

        mainHandler.post(() -> {
            if (!closed && listener != null) {
                listener.onError(message);
            }
        });
    }

    public void stop() {

        learning = false;
        running = false;

        if (cameraProvider != null) {

            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                sendError("Camera stop failed: "
                        + e.getMessage());
            }

            cameraProvider = null;
        }

        if (!closed) {
            cameraExecutor.execute(engine::close);
        }
    }

    public void shutdown() {

        if (closed) return;

        learning = false;
        running = false;
        closed = true;

        if (cameraProvider != null) {

            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }

            cameraProvider = null;
        }

        cameraExecutor.execute(() -> {
            engine.close();
            cameraExecutor.shutdown();
        });
    }
}
