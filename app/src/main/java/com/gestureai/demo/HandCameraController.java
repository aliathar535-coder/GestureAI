
package com.gestureai.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
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
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private volatile ProcessCameraProvider cameraProvider;

    private volatile boolean running = false;
    private volatile boolean starting = false;
    private volatile boolean learning = false;
    private volatile boolean closed = false;

    private volatile int generation = 0;

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

        engine = new HandGestureEngine(this.context);

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public synchronized void start() {

        if (closed || running || starting) {
            return;
        }

        starting = true;

        final int currentGeneration = ++generation;

        cameraExecutor.execute(() -> {

            try {

                engine.initialize();

                mainHandler.post(() -> {

                    if (closed || currentGeneration != generation) {
                        starting = false;
                        return;
                    }

                    openCamera(currentGeneration);
                });

            } catch (Exception e) {

                mainHandler.post(() -> {

                    if (currentGeneration != generation || closed) {
                        return;
                    }

                    starting = false;
                    running = false;

                    sendError("Hand recognition model initialize nahi hua");
                });
            }
        });
    }

    private void openCamera(int currentGeneration) {

        if (closed || currentGeneration != generation) {
            starting = false;
            return;
        }

        try {

            ListenableFuture<ProcessCameraProvider> future =
                    ProcessCameraProvider.getInstance(context);

            future.addListener(() -> {

                if (closed || currentGeneration != generation) {
                    starting = false;
                    return;
                }

                try {

                    cameraProvider = future.get();

                    Preview preview = new Preview.Builder()
                            .build();

                    preview.setSurfaceProvider(
                            previewView.getSurfaceProvider()
                    );

                    ImageAnalysis analysis =
                            new ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .setOutputImageFormat(
                                            ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                                    )
                                    .build();

                    analysis.setAnalyzer(
                            cameraExecutor,
                            this::analyzeFrame
                    );

                    cameraProvider.unbindAll();

                    cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                    );

                    if (closed || currentGeneration != generation) {
                        cameraProvider.unbindAll();
                        starting = false;
                        return;
                    }

                    running = true;
                    starting = false;

                    lastGesture = "";
                    lastGestureTime = 0;

                } catch (Exception e) {

                    starting = false;
                    running = false;

                    sendError(
                            "Camera start nahi hua: " +
                                    e.getMessage()
                    );
                }

            }, mainHandler::post);

        } catch (Exception e) {

            starting = false;
            running = false;

            sendError("Camera open nahi hua");
        }
    }

    private void analyzeFrame(ImageProxy image) {

        if (image == null) return;

        Bitmap bitmap = null;
        Bitmap rotatedBitmap = null;

        try {

            if (!running || closed) {
                return;
            }

            bitmap = image.toBitmap();

            if (bitmap == null) {
                return;
            }

            int rotation = image.getImageInfo()
                    .getRotationDegrees();

            Bitmap inputBitmap = bitmap;

            if (rotation != 0) {

                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);

                rotatedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );

                inputBitmap = rotatedBitmap;
            }

            if (learning) {

                int progress = engine.addLearningFrame(inputBitmap);

                if (progress >= 0) {

                    if (progress >= 5) {
                        learning = false;
                    }

                    final int count = progress;

                    mainHandler.post(() -> {

                        if (!closed && listener != null) {
                            listener.onLearningProgress(count);
                        }
                    });
                }

            } else {

                JSONObject result = engine.recognize(inputBitmap);

                if (result != null) {

                    String name = result.optString("name", "");
                    String action = result.optString("action", "");

                    long now = System.currentTimeMillis();

                    if (!name.isEmpty()
                            && !action.isEmpty()
                            && (!name.equals(lastGesture)
                            || now - lastGestureTime > 3000)) {

                        lastGesture = name;
                        lastGestureTime = now;

                        mainHandler.post(() -> {

                            if (!closed && listener != null) {
                                listener.onGestureRecognized(
                                        name,
                                        action
                                );
                            }
                        });
                    }
                }
            }

        } catch (Exception e) {

            sendError("Hand frame process nahi hua");

        } finally {

            if (rotatedBitmap != null
                    && rotatedBitmap != bitmap
                    && !rotatedBitmap.isRecycled()) {
                rotatedBitmap.recycle();
            }

            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }

            image.close();
        }
    }

    public synchronized boolean startLearning(
            String name,
            String action) {

        if (closed || !running || starting || learning) {
            return false;
        }

        boolean started = engine.startLearning(name, action);

        if (started) {
            learning = true;
        }

        return started;
    }

    public synchronized void cancelLearning() {

        learning = false;

        if (!closed) {
            engine.cancelLearning();
        }
    }

    public synchronized boolean removeGesture(String name) {

        if (closed) return false;

        return engine.removeGesture(name);
    }

    public synchronized JSONArray getSavedGestures() {

        if (closed) {
            return new JSONArray();
        }

        return engine.getSavedData();
    }

    public synchronized void stop() {

        generation++;

        starting = false;
        running = false;
        learning = false;

        lastGesture = "";
        lastGestureTime = 0;

        ProcessCameraProvider provider = cameraProvider;
        cameraProvider = null;

        if (provider != null) {

            try {
                provider.unbindAll();
            } catch (Exception ignored) {
            }
        }

        if (!closed && !cameraExecutor.isShutdown()) {

            cameraExecutor.execute(() -> {
                engine.close();
            });
        }
    }

    public synchronized void shutdown() {

        if (closed) return;

        closed = true;

        generation++;

        starting = false;
        running = false;
        learning = false;

        ProcessCameraProvider provider = cameraProvider;
        cameraProvider = null;

        if (provider != null) {

            try {
                provider.unbindAll();
            } catch (Exception ignored) {
            }
        }

        if (!cameraExecutor.isShutdown()) {

            cameraExecutor.execute(() -> {
                engine.close();
            });

            cameraExecutor.shutdown();
        }
    }

    private void sendError(String message) {

        mainHandler.post(() -> {

            if (!closed && listener != null) {
                listener.onError(message);
            }
        });
    }
}
