
package com.gestureai.demo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class FloatingOverlayService extends Service {

    private WindowManager wm;
    private TextView plus;
    private View panelView;

    private final ArrayList<Item> items = new ArrayList<>();

    private static final String PREF = "gesture_float_data";
    private static final String KEY = "buttons";

    private int nextNumber = 1;

    private class Item {
        int number;
        String command;
        int x;
        int y;
        boolean enabled = true;
        TextView view;
        WindowManager.LayoutParams params;

        float startX, startY;
        int initialX, initialY;
        boolean moved;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 23 &&
                !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        loadItems();
        showPlus();
    }

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
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
                        size, size,
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
        v.setTextSize(18);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.rgb(30, 110, 230));
        v.setElevation(dp(5));
        return v;
    }

    private void showPlus() {
        plus = makeButton("+");
        plus.setTextSize(27);
        plusParamsSetup();
        plus.setOnClickListener(v -> showManager());
        wm.addView(plus, makeParams(dp(25), dp(180), dp(58)));
    }

    private void plusParamsSetup() {
        // Plus button settings are handled by WindowManager.
    }

    private void showPanel(String heading) {
        closePanel();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 31, 52));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(65, 120, 220));
        root.setBackground(bg);

        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        panelView = root;

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        dp(330),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        overlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.CENTER;
        p.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        try {
            wm.addView(panelView, p);
        } catch (Exception e) {
            panelView = null;
            Toast.makeText(this,
                    "Panel nahi khul paya",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private TextView action(String text, Runnable task) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setBackgroundColor(Color.rgb(35, 95, 180));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0, dp(44), 1
                );
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));

        b.setOnClickListener(v -> task());
        b.setTag(lp);
        return b;
    }

    private void addActionRow(LinearLayout parent,
                              TextView... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (TextView b : buttons) {
            row.addView(b,
                    (LinearLayout.LayoutParams) b.getTag());
        }

        parent.addView(row);
    }

    private EditText makeInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint(hint);
        input.setText(value);
        input.setPadding(dp(10), dp(5), dp(10), dp(5));
        input.setBackgroundColor(Color.rgb(43, 55, 78));
        return input;
    }

    private void showManager() {
        showPanel("⚙ Floating Button Manager");

        if (panelView == null) return;

        LinearLayout root = (LinearLayout) panelView;

        EditText input = makeInput(
                "Naye button ka naam likhein", "");

        root.addView(input);

        addActionRow(root,
                action("＋ Add Button", () -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Naam likhein",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    addItem(name);
                    showManager();
                }),
                action("Close", this::closePanel)
        );

        TextView listTitle = new TextView(this);
        listTitle.setText("Aapke Floating Buttons:");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(16);
        listTitle.setPadding(0, dp(12), 0, dp(8));
        root.addView(listTitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (Item item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(7), dp(7), dp(7), dp(7));
            row.setBackgroundColor(Color.rgb(34, 47, 69));

            TextView name = new TextView(this);
            name.setText(item.number + ". " + item.command
                    + (item.enabled ? "  [ON]" : "  [OFF]"));
            name.setTextColor(Color.WHITE);
            name.setTextSize(15);
            row.addView(name);

            addActionRow(row,
                    action("Edit", () -> showEdit(item)),
                    action(item.enabled ? "Disable" : "Enable",
                            () -> {
                                item.enabled = !item.enabled;
                                updateButton(item);
                                saveItems();
                                showManager();
                            }),
                    action("Test", () -> {
                        closePanel();
                        performItem(item);
                    })
            );

            addActionRow(row,
                    action("Remove", () -> showRemoveConfirm(item))
            );

            LinearLayout.LayoutParams rowLp =
                    new LinearLayout.LayoutParams(
                            -1, -2);
            rowLp.setMargins(0, dp(4), 0, dp(4));
            list.addView(row, rowLp);
        }

        scroll.addView(list);
        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, dp(260)));
    }

    private void showEdit(Item item) {
        showPanel("Edit Button " + item.number);

        if (panelView == null) return;

        LinearLayout root = (LinearLayout) panelView;
        EditText input = makeInput(
                "Button ka naya naam", item.command);
        root.addView(input);

        addActionRow(root,
                action("Save", () -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Naam khali nahi ho sakta",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    item.command = name;
                    updateButton(item);
                    saveItems();
                    showManager();
                }),
                action("Cancel", this::showManager)
        );
    }

    private void showRemoveConfirm(Item item) {
        showPanel("Remove Button?");

        if (panelView == null) return;

        LinearLayout root = (LinearLayout) panelView;

        TextView message = new TextView(this);
        message.setText("Button " + item.number +
                " (" + item.command + ") ko hatana hai?");
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        root.addView(message);

        addActionRow(root,
                action("Yes, Remove", () -> {
                    removeItem(item);
                    showManager();
                }),
                action("Cancel", this::showManager)
        );
    }

    private void closePanel() {
        if (panelView != null && wm != null) {
            try {
                wm.removeView(panelView);
            } catch (Exception ignored) {
            }
            panelView = null;
        }
    }

    private void addItem(String command) {
        Item item = new Item();
        item.number = nextNumber++;
        item.command = command;
        item.x = dp(25);
        item.y = dp(260 + (items.size() % 5) * 70);

        item.view = makeButton(String.valueOf(item.number));
        item.params = makeParams(item.x, item.y, dp(58));

        attachTouch(item);
        wm.addView(item.view, item.params);
        items.add(item);

        saveItems();

        Toast.makeText(this,
                "Button " + item.number + " save ho gaya",
                Toast.LENGTH_SHORT).show();
    }

    private void updateButton(Item item) {
        if (item.view == null) return;

        item.view.setText(String.valueOf(item.number));

        if (item.enabled) {
            item.view.setBackgroundColor(
                    Color.rgb(30, 110, 230));
            item.view.setAlpha(1f);
        } else {
            item.view.setBackgroundColor(
                    Color.rgb(90, 90, 90));
            item.view.setAlpha(0.65f);
        }
    }

    private void removeItem(Item item) {
        try {
            if (item.view != null) {
                wm.removeView(item.view);
            }
        } catch (Exception ignored) {
        }

        items.remove(item);
        saveItems();

        Toast.makeText(this,
                "Button remove ho gaya",
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

                    if (Math.abs(dx) > dp(8) ||
                            Math.abs(dy) > dp(8)) {
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
        if (!item.enabled) {
            Toast.makeText(this,
                    "Yeh button OFF hai",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!GestureAccessibilityService.isReady()) {
            Toast.makeText(this,
                    "Accessibility ON karein",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        float x = item.params.x + dp(29);
        float y = item.params.y + dp(29);

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
                item.enabled = obj.optBoolean("enabled", true);

                item.view = makeButton(
                        String.valueOf(item.number));
                item.params = makeParams(
                        item.x, item.y, dp(58));

                updateButton(item);
                attachTouch(item);
                wm.addView(item.view, item.params);

                items.add(item);
                nextNumber = Math.max(
                        nextNumber, item.number + 1);
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
                obj.put("enabled", item.enabled);
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
    public int onStartCommand(Intent intent,
                              int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        closePanel();

        if (wm != null) {
            if (plus != null) {
                try {
                    wm.removeView(plus);
                } catch (Exception ignored) {
                }
                plus = null;
            }

            for (Item item : items) {
                try {
                    if (item.view != null) {
                        wm.removeView(item.view);
                    }
                } catch (Exception ignored) {
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
            
