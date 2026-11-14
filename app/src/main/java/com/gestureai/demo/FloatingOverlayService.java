package com.gestureai.demo;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class FloatingOverlayService extends Service {

    private WindowManager wm;
    private TextView plus;
    private LinearLayout panel;

    private final ArrayList<Item> items = new ArrayList<>();

    private static final String PREF = "gesture_float_data";
    private static final String PROFILES = "app_profiles";

    private static final String VOICE_COMMAND_ACTION =
            "com.gestureai.demo.VOICE_COMMAND";

    private String activeProfile = "default";

    private Handler handler = new Handler(Looper.getMainLooper());
    private VoiceCommandHelper voiceHelper;
    private CustomVoiceCommandStore customVoiceStore;

    private class Item {
        int number;
        String command;
        boolean enabled = true;
        TextView view;
        WindowManager.LayoutParams params;
        float startX, startY;
        int initialX, initialY;
        boolean moved;
    }

    private final Runnable profileChecker = new Runnable() {
        @Override
        public void run() {

            String pkg =
                    GestureAccessibilityService.getForegroundPackage();

            if (pkg != null && !pkg.isEmpty()
                    && !pkg.equals(getPackageName())
                    && !pkg.equals(activeProfile)) {

                switchProfile(pkg);
            }

            if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    private int overlayType() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
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
        v.setTextSize(18);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.rgb(30, 110, 230));
        v.setElevation(dp(5));

        return v;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        customVoiceStore = new CustomVoiceCommandStore(this);

        if (android.os.Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (wm == null) {
            stopSelf();
            return;
        }

        activeProfile =
                GestureAccessibilityService.getForegroundPackage();

        if (activeProfile == null || activeProfile.isEmpty()
                || activeProfile.equals(getPackageName())) {
            activeProfile = "default";
        }

        showPlus();
        loadProfile(activeProfile);

        handler.postDelayed(profileChecker, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null
                && VOICE_COMMAND_ACTION.equals(intent.getAction())) {

            String command =
                    intent.getStringExtra("voice_command");

            if (command != null && !command.trim().isEmpty()) {
                handleVoiceCommand(command);
            }
        }

        return START_STICKY;
    }

    private void showPlus() {

        plus = makeButton("+");
        plus.setTextSize(27);
        plus.setOnClickListener(v -> showManager());

        wm.addView(
                plus,
                makeParams(dp(25), dp(180), dp(58))
        );
    }

    private void switchProfile(String packageName) {

        if (packageName == null || packageName.isEmpty()) return;

        saveProfile();

        for (Item item : items) {
            try {
                if (item.view != null) {
                    wm.removeView(item.view);
                }
            } catch (Exception ignored) {
            }
        }

        items.clear();
        closePanel();

        activeProfile = packageName;
        loadProfile(activeProfile);
    }

    private void loadProfile(String packageName) {

        items.clear();

        String all = getSharedPreferences(PREF, MODE_PRIVATE)
                .getString(PROFILES, "{}");

        try {
            JSONObject profiles = new JSONObject(all);
            JSONArray array = profiles.optJSONArray(packageName);

            if (array == null) return;

            for (int i = 0; i < array.length(); i++) {

                JSONObject obj = array.getJSONObject(i);

                Item item = new Item();

                item.number = obj.getInt("number");
                item.command = obj.getString("command");
                item.enabled = obj.optBoolean("enabled", true);

                item.view = makeButton(
                        String.valueOf(item.number)
                );

                item.params = makeParams(
                        obj.getInt("x"),
                        obj.getInt("y"),
                        dp(58)
                );

                updateButton(item);
                attachTouch(item);

                wm.addView(item.view, item.params);
                items.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveProfile() {

        try {
            String all = getSharedPreferences(PREF, MODE_PRIVATE)
                    .getString(PROFILES, "{}");

            JSONObject profiles = new JSONObject(all);
            JSONArray array = new JSONArray();

            for (Item item : items) {

                JSONObject obj = new JSONObject();

                obj.put("number", item.number);
                obj.put("command", item.command);
                obj.put("x", item.params.x);
                obj.put("y", item.params.y);
                obj.put("enabled", item.enabled);

                array.put(obj);
            }

            profiles.put(activeProfile, array);

            getSharedPreferences(PREF, MODE_PRIVATE)
                    .edit()
                    .putString(PROFILES, profiles.toString())
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int nextNumber() {

        int max = 0;

        for (Item item : items) {
            max = Math.max(max, item.number);
        }

        return max + 1;
    }

    private void showManager() {

        closePanel();

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(15), dp(15), dp(15));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 31, 52));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(65, 120, 220));

        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("Floating Buttons");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);

        panel.addView(title);

        EditText input = makeInput("Naye button ka naam");
        panel.addView(input);

        addRow(panel,
                action("Add", () -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) return;

                    addItem(name);
                    showManager();
                }),
                action("Voice", this::startContinuousVoice),
                action("Close", this::closePanel)
        );

        ScrollView scroll = new ScrollView(this);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (Item item : items) {

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(6), dp(6), dp(6), dp(6));
            row.setBackgroundColor(Color.rgb(34, 47, 69));

            TextView name = new TextView(this);

            name.setText(item.number + ". " + item.command
                    + (item.enabled ? " [ON]" : " [OFF]"));

            name.setTextColor(Color.WHITE);
            row.addView(name);

            addRow(row,
                    action("Edit", () -> showEdit(item)),

                    action(item.enabled ? "OFF" : "ON", () -> {
                        item.enabled = !item.enabled;
                        updateButton(item);
                        saveProfile();
                        showManager();
                    }),

                    action("Test", () -> {
                        closePanel();
                        performItem(item);
                    })
            );

            addRow(row,
                    action("Remove", () -> showRemove(item))
            );

            list.addView(row);
        }

        scroll.addView(list);

        panel.addView(scroll,
                new LinearLayout.LayoutParams(-1, dp(240)));

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        dp(330),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        overlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.CENTER;
        p.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        try {
            wm.addView(panel, p);
        } catch (Exception e) {
            panel = null;

            Toast.makeText(this, "Panel nahi khula",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private EditText makeInput(String hint) {

        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint(hint);
        input.setBackgroundColor(Color.rgb(43, 55, 78));

        return input;
    }

    private TextView action(String text, Runnable task) {

        TextView b = new TextView(this);

        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5), dp(8), dp(5), dp(8));
        b.setBackgroundColor(Color.rgb(35, 95, 180));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, dp(42), 1);

        lp.setMargins(dp(2), dp(3), dp(2), dp(3));

        b.setTag(lp);

        b.setOnClickListener(v -> {
            if (task != null) {
                task.run();
            }
        });

        return b;
    }

    private void addRow(LinearLayout parent, TextView... buttons) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (TextView b : buttons) {
            row.addView(
                    b,
                    (LinearLayout.LayoutParams) b.getTag()
            );
        }

        parent.addView(row);
    }

    private void showEdit(Item item) {

        closePanel();

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(15), dp(15), dp(15));
        panel.setBackgroundColor(Color.rgb(20, 31, 52));

        EditText input = makeInput("Naya naam");
        input.setText(item.command);

        panel.addView(input);

        addRow(panel,
                action("Save", () -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) return;

                    item.command = name;
                    saveProfile();
                    showManager();
                }),

                action("Cancel", this::showManager)
        );

        showSimplePanel();
    }

    private void showRemove(Item item) {

        closePanel();

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(15), dp(15), dp(15));
        panel.setBackgroundColor(Color.rgb(20, 31, 52));

        TextView msg = new TextView(this);

        msg.setText("Button " + item.number + " hatana hai?");
        msg.setTextColor(Color.WHITE);

        panel.addView(msg);

        addRow(panel,
                action("Yes Remove", () -> {

                    try {
                        wm.removeView(item.view);
                    } catch (Exception ignored) {
                    }

                    items.remove(item);
                    saveProfile();
                    showManager();
                }),

                action("Cancel", this::showManager)
        );

        showSimplePanel();
    }

    private void showSimplePanel() {

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        dp(310),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        overlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.CENTER;
        p.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        try {
            wm.addView(panel, p);
        } catch (Exception e) {
            panel = null;
        }
                           }
        private void closePanel() {

        if (panel != null && wm != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }

            panel = null;
        }
    }

    private void addItem(String command) {

        Item item = new Item();

        item.number = nextNumber();
        item.command = command;

        item.view = makeButton(String.valueOf(item.number));

        item.params = makeParams(
                dp(25),
                dp(260 + (items.size() % 5) * 70),
                dp(58)
        );

        attachTouch(item);

        wm.addView(item.view, item.params);

        items.add(item);
        saveProfile();
    }

    private void updateButton(Item item) {

        if (item.view == null) return;

        if (item.enabled) {
            item.view.setBackgroundColor(Color.rgb(30, 110, 230));
            item.view.setAlpha(1f);
        } else {
            item.view.setBackgroundColor(Color.GRAY);
            item.view.setAlpha(0.55f);
        }
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

                    if (Math.abs(dx) > dp(8)
                            || Math.abs(dy) > dp(8)) {
                        item.moved = true;
                    }

                    if (item.moved) {

                        item.params.x = item.initialX + (int) dx;
                        item.params.y = item.initialY + (int) dy;

                        try {
                            wm.updateViewLayout(item.view, item.params);
                        } catch (Exception ignored) {
                        }
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    if (item.moved) {
                        saveProfile();
                    } else {
                        performItem(item);
                    }

                    return true;
            }

            return false;
        });
    }

    private void performItem(Item item) {

        if (item == null) return;

        if (!item.enabled) {
            Toast.makeText(this, "Button OFF hai",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!GestureAccessibilityService.isReady()) {
            Toast.makeText(this, "Accessibility ON karein",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        float x = item.params.x + dp(29);
        float y = item.params.y + dp(29);

        GestureAccessibilityService.tap(x, y);
    }

    private void startContinuousVoice() {

        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this,
                    "Pehle Microphone permission dijiye",
                    Toast.LENGTH_LONG).show();

            return;
        }

        try {

            Intent intent = new Intent(
                    this,
                    VoiceForegroundService.class
            );

            intent.setAction(
                    VoiceForegroundService.ACTION_START
            );

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(this,
                    "Voice start karne ki request bheji",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {

            Toast.makeText(this,
                    "Voice start nahi ho paya. Gesture AI kholiye.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startVoice() {

        if (voiceHelper != null) {
            voiceHelper.stop();
        }

        voiceHelper = new VoiceCommandHelper();

        Toast.makeText(this,
                "Button ka naam ya number boliye",
                Toast.LENGTH_LONG).show();

        voiceHelper.start(this,
                new VoiceCommandHelper.Listener() {

                    @Override
                    public void onCommand(String command) {

                        Item found = findVoiceItem(command);

                        if (voiceHelper != null) {
                            voiceHelper.stop();
                            voiceHelper = null;
                        }

                        if (found != null) {
                            closePanel();
                            performItem(found);
                        } else {
                            Toast.makeText(
                                    FloatingOverlayService.this,
                                    "Button nahi mila",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String error) {

                        if (voiceHelper != null) {
                            voiceHelper.stop();
                            voiceHelper = null;
                        }

                        Toast.makeText(
                                FloatingOverlayService.this,
                                error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private String normalize(String value) {

        if (value == null) return "";

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0900-\\u097F]", "");
    }

    private Item findVoiceItem(String spokenCommand) {

        String spoken = normalize(spokenCommand);

        if (spoken.isEmpty()) return null;

        // पहले पूरा नाम मिलाएँ
        for (Item item : items) {

            if (item.enabled
                    && normalize(item.command).equals(spoken)) {
                return item;
            }
        }

        // फिर नाम के अंदर बोले गए शब्द खोजें
        for (Item item : items) {

            String savedName = normalize(item.command);

            if (item.enabled
                    && !savedName.isEmpty()
                    && spoken.contains(savedName)) {
                return item;
            }
        }

        // Button number से खोजें
        try {
            int number = Integer.parseInt(spoken);

            for (Item item : items) {
                if (item.enabled && item.number == number) {
                    return item;
                }
            }
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    private void handleVoiceCommand(String spokenCommand) {

        if (spokenCommand == null || spokenCommand.trim().isEmpty()) {
            return;
        }

        // सबसे पहले Custom Voice Command जाँचें
        if (customVoiceStore != null) {

            String customAction =
                    customVoiceStore.findAction(spokenCommand);

            if (customAction != null && !customAction.trim().isEmpty()) {

                GestureActionExecutor executor =
                        new GestureActionExecutor(this);

                boolean success = executor.execute(customAction);

                Toast.makeText(
                        this,
                        success
                                ? "Custom Voice Command chala: " + spokenCommand
                                : "Custom Command nahi chal paya",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
        }

        // अगर Custom Command नहीं मिला तो Floating Button खोजें
        Item found = findVoiceItem(spokenCommand);

        if (found != null) {
            performItem(found);
            return;
        }

        // आखिरी विकल्प: सामान्य फोन कमांड या App खोलना
        GestureActionExecutor executor =
                new GestureActionExecutor(this);

        boolean success = executor.execute(spokenCommand);

        if (!success) {
            Toast.makeText(
                    this,
                    "Command nahi samajh aaya: " + spokenCommand,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroy() {

        if (handler != null) {
            handler.removeCallbacks(profileChecker);
        }

        if (voiceHelper != null) {
            voiceHelper.stop();
            voiceHelper = null;
        }

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
