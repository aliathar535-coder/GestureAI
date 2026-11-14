
package com.gestureai.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class GestureAccessibilityService extends AccessibilityService {

    private static GestureAccessibilityService instance;

    private static volatile String foregroundPackage = "";

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

        CharSequence packageName = event.getPackageName();

        if (packageName != null &&
                !getInstancePackageName().contentEquals(packageName)) {

            foregroundPackage = packageName.toString();
        }
    }

    private String getInstancePackageName() {
        return getPackageName();
    }

    public static boolean performGlobalAction(int action) {

        GestureAccessibilityService service = instance;

        if (service == null) return false;

        return service.performGlobalAction(action);
    }

    private static boolean canDispatch() {
        return instance != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static void tap(float x, float y) {

        if (!canDispatch()) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        path, 0, 100);

        GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();

        instance.dispatchGesture(gesture, null, null);
    }

    public static void longPress(float x, float y) {

        if (!canDispatch()) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        path, 0, 900);

        GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();

        instance.dispatchGesture(gesture, null, null);
    }

    public static void doubleTap(float x, float y) {

        tap(x, y);

        if (instance != null) {
            instance.getMainThreadHandler().postDelayed(
                    () -> tap(x, y), 180);
        }
    }

    public static void swipe(
            float startX,
            float startY,
            float endX,
            float endY,
            long duration) {

        if (!canDispatch()) return;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        long safeDuration = Math.max(
                100, Math.min(duration, 5000));

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        path, 0, safeDuration);

        GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();

        instance.dispatchGesture(gesture, null, null);
    }

    public static boolean scroll(boolean up) {

        if (!canDispatch()) return false;

        GestureAccessibilityService service = instance;

        if (service == null) return false;

        WindowManager wm = service.getSystemService(
                WindowManager.class);

        if (wm == null) return false;

        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        float centerX = size.x / 2f;
        float startY = up ? size.y * 0.70f : size.y * 0.30f;
        float endY = up ? size.y * 0.30f : size.y * 0.70f;

        swipe(centerX, startY, centerX, endY, 400);

        return true;
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
