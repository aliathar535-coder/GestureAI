
package com.gestureai.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
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
                !getPackageName().contentEquals(packageName)) {

            foregroundPackage = packageName.toString();
        }
    }

    private static boolean canDispatch() {
        return instance != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static void tap(float x, float y) {

        if (!canDispatch()) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 0, 100
                )
        );

        instance.dispatchGesture(
                builder.build(), null, null
        );
    }

    public static void longPress(float x, float y) {

        if (!canDispatch()) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 0, 900
                )
        );

        instance.dispatchGesture(
                builder.build(), null, null
        );
    }

    public static void doubleTap(float x, float y) {

        if (!canDispatch()) return;

        Path firstPath = new Path();
        firstPath.moveTo(x, y);

        Path secondPath = new Path();
        secondPath.moveTo(x, y);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        firstPath, 0, 80
                )
        );

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        secondPath, 180, 80
                )
        );

        instance.dispatchGesture(
                builder.build(), null, null
        );
    }

    public static void swipe(
            float startX,
            float startY,
            float endX,
            float endY,
            long duration
    ) {

        if (!canDispatch()) return;

        long safeDuration = Math.max(
                100,
                Math.min(duration, 5000)
        );

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 0, safeDuration
                )
        );

        instance.dispatchGesture(
                builder.build(), null, null
        );
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
