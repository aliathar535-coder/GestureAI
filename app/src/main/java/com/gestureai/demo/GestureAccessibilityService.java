
package com.gestureai.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
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

        if (packageName != null) {
            foregroundPackage = packageName.toString();
        }
    }

    public static void tap(float x, float y) {

        if (instance == null) return;

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

        if (instance == null) return;

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

        if (instance == null) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 0, 80
                )
        );

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 180, 80
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

        if (instance == null) return;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription.Builder builder =
                new GestureDescription.Builder();

        builder.addStroke(
                new GestureDescription.StrokeDescription(
                        path, 0, duration
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
