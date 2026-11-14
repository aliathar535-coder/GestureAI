package com.gestureai.demo;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class FloatingOverlayService extends Service {

    private static final String PREF = "gesture_float_data";
    private static final String PROFILES = "app_profiles";

    private static final String ACTION_VOICE_COMMAND =
            "com.gestureai.demo.VOICE_COMMAND";

    private WindowManager wm;
    private LinearLayout panel;
    private TextView plus;

    private final ArrayList<Item> items = new ArrayList<>();

    private CustomVoiceCommandStore commandStore;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private String activeProfile = "default";

    private boolean panelVisible = false;
    private boolean serviceDestroyed = false;

    private int nextNumber = 1;

    private final Runnable profileChecker = new Runnable() {
        @Override
        public void run() {

            if (serviceDestroyed) {
                return;
            }

            try {

                String foreground =
                        GestureAccessibilityService.getForegroundPackage();

                if (foreground == null || foreground.trim().isEmpty()) {
                    foreground = "default";
                }

                if (!foreground.equals(activeProfile)) {
                    switchProfile(foreground);
                }

            } catch (Exception ignored) {
            }

            handler.postDelayed(this, 1500);
        }
    };

    private static class ViewHolder {
        View view;
    }

    private static class Item {

        int number;
        String command;
        int x;
        int y;
        boolean enabled = true;

        TextView view;
        WindowManager.LayoutParams params;

        float downX;
        float downY;
        int originalX;
        int originalY;

        boolean moved;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        commandStore =
                new CustomVoiceCommandStore(getApplicationContext());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        serviceDestroyed = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {

            Toast.makeText(
                    this,
                    "Floating Window Permission जरूरी है",
                    Toast.LENGTH_LONG
            ).show();

            stopSelf();
            return;
        }

        createFloatingButton();

        loadProfile("default");

        handler.post(profileChecker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (wm == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null
                && ACTION_VOICE_COMMAND.equals(intent.getAction())) {

            String spokenCommand =
                    intent.getStringExtra("voice_command");

            handleVoiceCommand(spokenCommand);
        }

        return START_STICKY;
    }

    private void handleVoiceCommand(String spokenCommand) {

        if (spokenCommand == null || spokenCommand.trim().isEmpty()) {
            return;
        }

        String command = spokenCommand.trim();

        if (commandStore != null) {

            String customAction =
                    commandStore.findAction(command);

            if (customAction != null
                    && !customAction.trim().isEmpty()) {

                command = customAction.trim();
            }
        }

        boolean success = false;

        try {

            GestureActionExecutor executor =
                    new GestureActionExecutor(getApplicationContext());

            success = executor.execute(command);

        } catch (Exception ignored) {
        }

        Toast.makeText(
                getApplicationContext(),
                success
                        ? "Voice Command चल गया"
                        : "Command नहीं चला: " + command,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int getOverlayType() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }

        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams createParams(
            int width,
            int height,
            int gravity,
            int x,
            int y
    ) {

        return new WindowManager.LayoutParams(
                width,
                height,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        ) {{
            this.gravity = gravity;
            this.x = x;
            this.y = y;
        }};
    }

    private GradientDrawable background(
            int color,
            int radius
    ) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(radius);

        return drawable;
    }

    private void createFloatingButton() {

        if (wm == null || plus != null) {
            return;
        }

        plus = new TextView(this);

        plus.setText("+");
        plus.setTextColor(Color.WHITE);
        plus.setTextSize(25);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(background(0xFF1565C0, dp(30)));

        WindowManager.LayoutParams params = createParams(
                dp(58),
                dp(58),
                Gravity.TOP | Gravity.START,
                dp(12),
                dp(180)
        );

        try {

            wm.addView(plus, params);

            plus.setOnClickListener(v -> togglePanel());

            plus.setOnTouchListener(new View.OnTouchListener() {

                float startX;
                float startY;
                int originalX;
                int originalY;
                boolean moved;

                @Override
                public boolean onTouch(View view, MotionEvent event) {

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:

                            startX = event.getRawX();
                            startY = event.getRawY();

                            originalX = params.x;
                            originalY = params.y;

                            moved = false;

                            return false;

                        case MotionEvent.ACTION_MOVE:

                            float dx = event.getRawX() - startX;
                            float dy = event.getRawY() - startY;

                            if (Math.abs(dx) > dp(6)
                                    || Math.abs(dy) > dp(6)) {

                                moved = true;
                            }

                            if (moved) {

                                params.x = originalX + (int) dx;
                                params.y = originalY + (int) dy;

                                try {
                                    wm.updateViewLayout(plus, params);
                                } catch (Exception ignored) {
                                }

                                return true;
                            }

                            return false;

                        case MotionEvent.ACTION_UP:
                            return moved;
                    }

                    return false;
                }
            });

        } catch (Exception e) {

            plus = null;
            stopSelf();
        }
    }

    private void togglePanel() {

        if (panelVisible) {
            removePanel();
        } else {
            showPanel();
        }
    }

    private void showPanel() {

        if (panelVisible || wm == null) {
            return;
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(background(0xFF202B3C, dp(16)));

        TextView title = new TextView(this);
        title.setText("GESTURE AI CONTROLS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(10));

        panel.addView(title);

        Button addButton = makeButton("＋ नया Floating Button");
        panel.addView(addButton);

        addButton.setOnClickListener(v -> showAddDialog());

        Button refreshButton = makeButton("Saved Buttons Refresh");
        panel.addView(refreshButton);

        refreshButton.setOnClickListener(v -> {
            removeAllItemViews();
            loadProfile(activeProfile);
        });

        Button testButton = makeButton("Accessibility Test");
        panel.addView(testButton);

        testButton.setOnClickListener(v -> testAccessibility());

        Button voiceButton = makeButton("Voice Control शुरू करें");
        panel.addView(voiceButton);

        voiceButton.setOnClickListener(v -> startContinuousVoice());

        Button closeButton = makeButton("बंद करें");
        panel.addView(closeButton);

        closeButton.setOnClickListener(v -> removePanel());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(panel);

        WindowManager.LayoutParams params = createParams(
                dp(280),
                dp(420),
                Gravity.TOP | Gravity.START,
                dp(12),
                dp(250)
        );

        try {

            wm.addView(scrollView, params);

            panel.setTag(scrollView);
            panelVisible = true;

        } catch (Exception e) {

            panel = null;
            panelVisible = false;

            Toast.makeText(
                    this,
                    "Panel नहीं खुल पाया",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private Button makeButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setBackground(background(0xFF30445F, dp(10)));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        params.setMargins(0, 0, 0, dp(7));

        button.setLayoutParams(params);

        return button;
    }

    private void removePanel() {

        if (panel == null || wm == null) {
            panelVisible = false;
            return;
        }

        try {

            Object tag = panel.getTag();

            if (tag instanceof View) {
                wm.removeView((View) tag);
            }

        } catch (Exception ignored) {
        }

        panel = null;
        panelVisible = false;
    }

    private void showAddDialog() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(8));

        EditText commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setHint("Action लिखें: back, home, volume up");

        layout.addView(commandInput);

        EditText xInput = new EditText(this);
        xInput.setSingleLine(true);
        xInput.setInputType(2);
        xInput.setHint("Screen X position (default 200)");

        layout.addView(xInput);

        EditText yInput = new EditText(this);
        yInput.setSingleLine(true);
        yInput.setInputType(2);
        yInput.setHint("Screen Y position (default 300)");

        layout.addView(yInput);

        new AlertDialog.Builder(this)
                .setTitle("नया Floating Button")
                .setView(layout)
                .setPositiveButton("सेव करें", (dialog, which) -> {

                    String command = commandInput.getText()
                            .toString().trim();

                    if (command.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Action लिखें",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    int x = readNumber(xInput.getText().toString(), 200);
                    int y = readNumber(yInput.getText().toString(), 300);

                    addItem(command, x, y);
                })
                .setNegativeButton("रद्द करें", null)
                .show();
    }

    private int readNumber(String value, int fallback) {

        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void addItem(String command, int x, int y) {

        Item item = new Item();

        item.number = nextNumber++;
        item.command = command;
        item.x = x;
        item.y = y;
        item.enabled = true;

        items.add(item);

        createItemView(item);

        saveProfile();

        Toast.makeText(
                this,
                "Floating Button सेव हो गया",
                Toast.LENGTH_SHORT
        ).show();
            }    private void createItemView(Item item) {

        if (wm == null || item == null || !item.enabled) {
            return;
        }

        TextView button = new TextView(this);

        button.setText(String.valueOf(item.number));
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(0xFF00897B, dp(26)));

        item.params = createParams(
                dp(52),
                dp(52),
                Gravity.TOP | Gravity.START,
                item.x,
                item.y
        );

        try {

            wm.addView(button, item.params);

            item.view = button;

            button.setOnClickListener(v -> performItem(item));

            button.setOnLongClickListener(v -> {
                showEditDialog(item);
                return true;
            });

            button.setOnTouchListener((view, event) ->
                    handleItemTouch(item, event));

        } catch (Exception e) {

            item.view = null;
            item.params = null;
        }
    }

    private boolean handleItemTouch(Item item, MotionEvent event) {

        if (item == null || item.params == null) {
            return false;
        }

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:

                item.downX = event.getRawX();
                item.downY = event.getRawY();

                item.originalX = item.params.x;
                item.originalY = item.params.y;

                item.moved = false;

                return false;

            case MotionEvent.ACTION_MOVE:

                float dx = event.getRawX() - item.downX;
                float dy = event.getRawY() - item.downY;

                if (Math.abs(dx) > dp(7)
                        || Math.abs(dy) > dp(7)) {

                    item.moved = true;
                }

                if (item.moved) {

                    item.params.x = item.originalX + (int) dx;
                    item.params.y = item.originalY + (int) dy;

                    item.x = item.params.x;
                    item.y = item.params.y;

                    try {
                        wm.updateViewLayout(item.view, item.params);
                    } catch (Exception ignored) {
                    }

                    return true;
                }

                return false;

            case MotionEvent.ACTION_UP:

                if (item.moved) {
                    saveProfile();
                    return true;
                }

                return false;
        }

        return false;
    }

    private void showEditDialog(Item item) {

        if (item == null) {
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(8));

        EditText commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setHint("Action लिखें");
        commandInput.setText(item.command);
        layout.addView(commandInput);

        EditText xInput = new EditText(this);
        xInput.setSingleLine(true);
        xInput.setInputType(2);
        xInput.setHint("X position");
        xInput.setText(String.valueOf(item.x));
        layout.addView(xInput);

        EditText yInput = new EditText(this);
        yInput.setSingleLine(true);
        yInput.setInputType(2);
        yInput.setHint("Y position");
        yInput.setText(String.valueOf(item.y));
        layout.addView(yInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Floating Button बदलें")
                .setView(layout)
                .setPositiveButton("सेव करें", (d, which) -> {

                    String command = commandInput.getText()
                            .toString().trim();

                    if (command.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Action खाली नहीं हो सकता",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    item.command = command;
                    item.x = readNumber(
                            xInput.getText().toString(),
                            item.x
                    );
                    item.y = readNumber(
                            yInput.getText().toString(),
                            item.y
                    );

                    removeItemView(item);
                    createItemView(item);
                    saveProfile();
                })
                .setNeutralButton("हटाएँ", (d, which) -> {
                    removeItem(item);
                })
                .setNegativeButton("रद्द करें", null)
                .create();

        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setType(getOverlayType());
            }
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Edit window नहीं खुल पाया",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void performItem(Item item) {

        if (item == null || !item.enabled) {
            return;
        }

        try {

            String action = item.command.trim();

            if (action.isEmpty()) {
                return;
            }

            GestureActionExecutor executor =
                    new GestureActionExecutor(this);

            executor.execute(action);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Action पूरा नहीं हुआ",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void removeItem(Item item) {

        if (item == null) {
            return;
        }

        removeItemView(item);

        items.remove(item);

        saveProfile();

        Toast.makeText(
                this,
                "Button हटा दिया गया",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void removeItemView(Item item) {

        if (item == null || item.view == null || wm == null) {
            return;
        }

        try {
            wm.removeView(item.view);
        } catch (Exception ignored) {
        }

        item.view = null;
        item.params = null;
    }

    private void removeAllItemViews() {

        for (Item item : items) {
            removeItemView(item);
        }

        items.clear();
    }

    private void switchProfile(String packageName) {

        if (packageName == null || packageName.trim().isEmpty()) {
            packageName = "default";
        }

        if (packageName.equals(activeProfile)) {
            return;
        }

        saveProfile();

        removeAllItemViews();

        activeProfile = packageName;

        loadProfile(packageName);
    }

    private void loadProfile(String profileName) {

        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = "default";
        }

        activeProfile = profileName;

        removeAllItemViews();

        try {

            String saved = getSharedPreferences(
                    PREF,
                    MODE_PRIVATE
            ).getString(PROFILES, "{}");

            JSONObject root = new JSONObject(saved);

            JSONArray array = root.optJSONArray(profileName);

            if (array == null) {
                nextNumber = 1;
                return;
            }

            int highestNumber = 0;

            for (int i = 0; i < array.length(); i++) {

                JSONObject object = array.optJSONObject(i);

                if (object == null) {
                    continue;
                }

                Item item = new Item();

                item.number = object.optInt("number", i + 1);
                item.command = object.optString("command", "");
                item.x = object.optInt("x", dp(200));
                item.y = object.optInt("y", dp(300));
                item.enabled = object.optBoolean("enabled", true);

                if (item.command.trim().isEmpty()) {
                    continue;
                }

                items.add(item);

                if (item.number > highestNumber) {
                    highestNumber = item.number;
                }

                if (item.enabled) {
                    createItemView(item);
                }
            }

            nextNumber = highestNumber + 1;

            if (nextNumber < 1) {
                nextNumber = 1;
            }

        } catch (Exception e) {

            items.clear();
            nextNumber = 1;
        }
    }

    private void saveProfile() {

        try {

            String saved = getSharedPreferences(
                    PREF,
                    MODE_PRIVATE
            ).getString(PROFILES, "{}");

            JSONObject root = new JSONObject(saved);

            JSONArray array = new JSONArray();

            for (Item item : items) {

                JSONObject object = new JSONObject();

                object.put("number", item.number);
                object.put("command", item.command);
                object.put("x", item.x);
                object.put("y", item.y);
                object.put("enabled", item.enabled);

                array.put(object);
            }

            root.put(activeProfile, array);

            getSharedPreferences(PREF, MODE_PRIVATE)
                    .edit()
                    .putString(PROFILES, root.toString())
                    .apply();

        } catch (Exception ignored) {
        }
    }

    private void testAccessibility() {

        String foreground =
                GestureAccessibilityService.getForegroundPackage();

        if (foreground == null || foreground.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Accessibility Service चालू करें",
                    Toast.LENGTH_LONG
            ).show();

        } else {

            Toast.makeText(
                    this,
                    "Accessibility Service काम कर रही है",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void startContinuousVoice() {

        try {

            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(
                        this,
                        "पहले Microphone Permission दें",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            Intent intent = new Intent(
                    this,
                    VoiceForegroundService.class
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(
                    this,
                    "Voice Control शुरू किया जा रहा है",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Voice Control शुरू नहीं हो पाया",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    public void onDestroy() {

        serviceDestroyed = true;

        handler.removeCallbacksAndMessages(null);

        removePanel();

        removeAllItemViews();

        if (plus != null && wm != null) {

            try {
                wm.removeView(plus);
            } catch (Exception ignored) {
            }

            plus = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
                                                }
