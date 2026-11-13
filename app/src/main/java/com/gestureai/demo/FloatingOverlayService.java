
package com.gestureai.demo;

import android.app.Service;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class FloatingOverlayService extends Service {

    private WindowManager wm;
    private TextView plus;
    private WindowManager.LayoutParams plusParams;

    private final ArrayList<Item> items = new ArrayList<>();

    private static final String PREF = "gesture_float_data";
    private static final String KEY = "buttons";

    private int nextNumber = 1;

    private class Item {
        int number;
        String command;
        int x;
        int y;
        TextView view;
        WindowManager.LayoutParams params;

        float startX;
        float startY;
        int initialX;
        int initialY;
        boolean moved;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        loadItems();
        showPlus();
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= 26) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams makeParams(
            int x, int y, int size) {

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        overlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x;
        p.y = y;

        return p;
    }

    private TextView makeButton(String text) {
        TextView v = new TextView(this);

        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(19);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.rgb(30, 110, 230));
        v.setElevation(10);

        return v;
    }

    private void showPlus() {
        plus = makeButton("+");
        plus.setTextSize(27);

        plusParams = makeParams(25, 180, 58);

        plus.setOnClickListener(v -> showAddDialog());

        wm.addView(plus, plusParams);
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Button ka naam likhein");

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(35, 10, 35, 10);
        layout.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Naya Floating Button")
                .setMessage("Jaise Send, Photo, Back ya Open")
                .setView(layout)
                .setPositiveButton("Save", (d, which) -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Naam likhna zaroori hai",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        addItem(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        if (Build.VERSION.SDK_INT >= 26) {
            dialog.getWindow();
            dialog.setOnShowListener(d -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setType(
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    );
                }
            });
        }

        dialog.show();

        if (Build.VERSION.SDK_INT >= 26
                && dialog.getWindow() != null) {
            dialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            );
        }
    }

    private void addItem(String command) {
        Item item = new Item();

        item.number = nextNumber++;
        item.command = command;
        item.x = 25;
        item.y = 260 + (items.size() % 5) * 70;

        item.view = makeButton(String.valueOf(item.number));
        item.params = makeParams(item.x, item.y, 58);

        attachTouch(item);

        wm.addView(item.view, item.params);
        items.add(item);

        saveItems();

        Toast.makeText(this,
                "Button " + item.number + " save ho gaya",
                Toast.LENGTH_SHORT).show();
    }

    private void attachTouch(Item item) {
        item.view.setOnTouchListener((view, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    item.startX = event.getRawX();
                    item.startY = event.getRawY();
                    item.initialX = item.params.x;
                    item.initialY = item.params.y;
                    item.moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - item.startX;
                    float dy = event.getRawY() - item.startY;

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        item.moved = true;
                    }

                    if (item.moved) {
                        item.params.x = item.initialX + (int) dx;
                        item.params.y = item.initialY + (int) dy;

                        wm.updateViewLayout(item.view, item.params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (item.moved) {
                        item.x = item.params.x;
                        item.y = item.params.y;
                        saveItems();
                    } else {
                        performItem(item);
                    }
                    return true;
            }

            return false;
        });
    }

    private void performItem(Item item) {
        if (!GestureAccessibilityService.isReady()) {
            Toast.makeText(this,
                    "Accessibility ON karein",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        float density = getResources()
                .getDisplayMetrics().density;

        float x = (item.params.x + 29 * density);
        float y = (item.params.y + 29 * density);

        GestureAccessibilityService.tap(x, y);
    }

    private void loadItems() {
        String saved = getSharedPreferences(PREF, MODE_PRIVATE)
                .getString(KEY, "[]");

        try {
            JSONArray array = new JSONArray(saved);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                Item item = new Item();

                item.number = obj.getInt("number");
                item.command = obj.getString("command");
                item.x = obj.getInt("x");
                item.y = obj.getInt("y");

                item.view = makeButton(
                        String.valueOf(item.number)
                );

                item.params = makeParams(item.x, item.y, 58);

                attachTouch(item);
                wm.addView(item.view, item.params);

                items.add(item);

                nextNumber = Math.max(
                        nextNumber, item.number + 1
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveItems() {
        JSONArray array = new JSONArray();

        try {
            for (Item item : items) {
                JSONObject obj = new JSONObject();

                obj.put("number", item.number);
                obj.put("command", item.command);
                obj.put("x", item.params.x);
                obj.put("y", item.params.y);

                array.put(obj);
            }

            getSharedPreferences(PREF, MODE_PRIVATE)
                    .edit()
                    .putString(KEY, array.toString())
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wm != null) {
            if (plus != null) {
                wm.removeView(plus);
                plus = null;
            }

            for (Item item : items) {
                if (item.view != null) {
                    wm.removeView(item.view);
                }
            }
        }

        items.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
