
package com.gestureai.demo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingOverlayService extends Service {

    private WindowManager windowManager;
    private TextView floatingButton;
    private WindowManager.LayoutParams params;

    private float startX, startY;
    private int initialX, initialY;
    private boolean moved;

    @Override
    public void onCreate() {
        super.onCreate();
        showFloatingButton();
    }

    private void showFloatingButton() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        floatingButton = new TextView(this);
        floatingButton.setText("+");
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setTextSize(25);
        floatingButton.setTypeface(null, Typeface.BOLD);
        floatingButton.setGravity(Gravity.CENTER);
        floatingButton.setBackgroundColor(Color.rgb(25, 110, 220));
        floatingButton.setElevation(12);

        int overlayType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                58,
                58,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30;
        params.y = 250;

        floatingButton.setOnTouchListener((view, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        moved = true;
                    }

                    params.x = initialX + (int) dx;
                    params.y = initialY + (int) dy;

                    windowManager.updateViewLayout(
                            floatingButton, params
                    );
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        Toast.makeText(
                                this,
                                "Floating Button Control",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                    return true;
            }

            return false;
        });

        windowManager.addView(floatingButton, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (floatingButton != null && windowManager != null) {
            windowManager.removeView(floatingButton);
            floatingButton = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
