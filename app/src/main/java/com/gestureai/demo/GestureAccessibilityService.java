
package com.gestureai.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class GestureAccessibilityService extends AccessibilityService {

    private static volatile GestureAccessibilityService instance;

    private static volatile String foregroundPackage = "";

    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static String getForegroundPackage() {
        return foregroundPackage;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null) return;

        int type = event.getEventType();

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        CharSequence packageName = event.getPackageName();

        if (packageName == null) return;

        String name = packageName.toString();

        if (!name.equals(getPackageName())) {
            foregroundPackage = name;
        }
    }

    public static boolean runGlobalAction(int action) {

        GestureAccessibilityService service = instance;

        if (service == null) return false;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return service.performGlobalAction(action);
        }

        mainHandler.post(() -> {
            GestureAccessibilityService current = instance;
            if (current != null) {
                current.performGlobalAction(action);
            }
        });

        return true;
    }

    private static boolean canDispatch() {
        return instance != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    private static boolean dispatchPath(
            Path path,
            long duration,
            Runnable onCompleted) {

        GestureAccessibilityService service = instance;

        if (service == null || path == null) return false;

        try {
            GestureDescription gesture =
                    new GestureDescription.Builder()
                            .addStroke(
                                    new GestureDescription.StrokeDescription(
                                            path,
                                            0,
                                            Math.max(50, duration)
                                    )
                            )
                            .build();

            return service.dispatchGesture(
                    gesture,
                    new GestureResultCallback() {

                        @Override
                        public void onCompleted(
                                GestureDescription gestureDescription) {

                            if (onCompleted != null) {
                                onCompleted.run();
                            }
                        }

                        @Override
                        public void onCancelled(
                                GestureDescription gestureDescription) {
                        }

                    },
                    mainHandler
            );

        } catch (Exception e) {
            return false;
        }
    }

    public static void tap(float x, float y) {

        if (!canDispatch()) return;

        mainHandler.post(() -> {

            Path path = new Path();
            path.moveTo(x, y);

            dispatchPath(path, 100, null);
        });
    }

    public static void longPress(float x, float y) {

        if (!canDispatch()) return;

        mainHandler.post(() -> {

            Path path = new Path();
            path.moveTo(x, y);

            dispatchPath(path, 900, null);
        });
    }

    public static void doubleTap(float x, float y) {

        if (!canDispatch()) return;

        mainHandler.post(() -> {

            Path firstPath = new Path();
            firstPath.moveTo(x, y);

            boolean started = dispatchPath(
                    firstPath,
                    100,
                    () -> {

                        mainHandler.postDelayed(() -> {

                            if (!canDispatch()) return;

                            Path secondPath = new Path();
                            secondPath.moveTo(x, y);

                            dispatchPath(secondPath, 100, null);

                        }, 150);
                    }
            );

            if (!started) {
                return;
            }
        });
    }

    public static void swipe(
            float startX,
            float startY,
            float endX,
            float endY,
            long duration) {

        if (!canDispatch()) return;

        mainHandler.post(() -> {

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            long safeDuration =
                    Math.max(100, Math.min(duration, 5000));

            dispatchPath(path, safeDuration, null);
        });
    }

    public static boolean scroll(boolean up) {

        GestureAccessibilityService service = instance;

        if (service == null) return false;

        WindowManager wm =
                (WindowManager) service.getSystemService(WINDOW_SERVICE);

        if (wm == null) return false;

        try {

            Display display = wm.getDefaultDisplay();

            Point size = new Point();
            display.getRealSize(size);

            if (size.x <= 0 || size.y <= 0) {
                return false;
            }

            float centerX = size.x / 2f;

            float startY = up
                    ? size.y * 0.70f
                    : size.y * 0.30f;

            float endY = up
                    ? size.y * 0.30f
                    : size.y * 0.70f;

            swipe(
                    centerX,
                    startY,
                    centerX,
                    endY,
                    400
            );

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {

        if (instance == this) {
            instance = null;
            foregroundPackage = "";
        }

        super.onDestroy();
    }
}
