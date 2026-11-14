
package com.gestureai.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class HandGestureActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 501;
    private static final long ACTION_COOLDOWN = 3000L;

    private PreviewView previewView;
    private TextView status;
    private EditText gestureName;
    private EditText gestureAction;
    private LinearLayout savedList;

    private HandCameraController controller;
    private GestureActionExecutor actionExecutor;

    private boolean cameraStartRequested = false;
    private boolean cameraReady = false;
    private boolean learning = false;
    private boolean activityDestroyed = false;

    private String pendingGestureName = "";
    private String pendingGestureAction = "";

    private final Map<String, Long> lastActionTimes =
            new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionExecutor = new GestureActionExecutor(this);

        createInterface();

        if (hasCameraPermission()) {
            initializeCameraController();
            refreshSavedGestures();
        } else {
            requestCameraPermission();
        }
    }

    private void createInterface() {

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        scrollView.addView(root);

        TextView heading = new TextView(this);
        heading.setText("Gesture AI - Hand Control");
        heading.setTextSize(23);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(8), 0, dp(16));
        root.addView(heading);

        previewView = new PreviewView(this);
        previewView.setId(View.generateViewId());
        previewView.setScaleType(
                PreviewView.ScaleType.FILL_CENTER
        );

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(300)
                );

        root.addView(previewView, previewParams);

        status = new TextView(this);
        status.setText("Camera शुरू होने का इंतजार...");
        status.setTextSize(16);
        status.setPadding(0, dp(14), 0, dp(14));
        root.addView(status);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("Gesture का नाम");
        nameLabel.setTextSize(16);
        root.addView(nameLabel);

        gestureName = new EditText(this);
        gestureName.setSingleLine(true);
        gestureName.setHint("जैसे: Open Hand");
        root.addView(gestureName);

        TextView actionLabel = new TextView(this);
        actionLabel.setText("इस Gesture पर कौन सा Action चले?");
        actionLabel.setTextSize(16);
        actionLabel.setPadding(0, dp(10), 0, 0);
        root.addView(actionLabel);

        gestureAction = new EditText(this);
        gestureAction.setSingleLine(true);
        gestureAction.setHint("जैसे: volume up, back, home");
        root.addView(gestureAction);

        Button learnButton = new Button(this);
        learnButton.setText("Gesture सीखें और सेव करें");
        root.addView(learnButton);

        learnButton.setOnClickListener(v -> beginLearning());

        Button cancelButton = new Button(this);
        cancelButton.setText("Learning रद्द करें");
        root.addView(cancelButton);

        cancelButton.setOnClickListener(v -> cancelLearning());

        Button refreshButton = new Button(this);
        refreshButton.setText("Saved Gestures Refresh करें");
        root.addView(refreshButton);

        refreshButton.setOnClickListener(
                v -> refreshSavedGestures()
        );

        TextView savedHeading = new TextView(this);
        savedHeading.setText("मेरे Saved Gestures");
        savedHeading.setTextSize(20);
        savedHeading.setPadding(0, dp(20), 0, dp(8));
        root.addView(savedHeading);

        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        root.addView(savedList);

        Button closeButton = new Button(this);
        closeButton.setText("वापस जाएँ");
        root.addView(closeButton);

        closeButton.setOnClickListener(v -> finish());

        setContentView(scrollView);
    }

    private int dp(float value) {

        return (int) (
                value *
                getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private boolean hasCameraPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {

        requestPermissions(
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION
        );
    }

    private void initializeCameraController() {

        if (controller != null || activityDestroyed) {
            return;
        }

        controller = new HandCameraController(
                this,
                this,
                previewView,
                new HandCameraController.Listener() {

                    @Override
                    public void onCameraReady() {

                        runOnUiThread(() -> {

                            if (activityDestroyed) {
                                return;
                            }

                            cameraReady = true;
                            cameraStartRequested = true;

                            status.setText(
                                    "Camera चालू है। हाथ दिखाएँ।"
                            );

                            startPendingLearning();
                        });
                    }

                    @Override
                    public void onLearningProgress(int progress) {

                        runOnUiThread(() -> {

                            if (activityDestroyed) {
                                return;
                            }

                            status.setText(
                                    "Gesture सीख रहा है: " +
                                    progress + "/5"
                            );

                            if (progress >= 5) {

                                learning = false;

                                pendingGestureName = "";
                                pendingGestureAction = "";

                                status.setText(
                                        "Gesture सफलतापूर्वक सेव हो गया!"
                                );

                                refreshSavedGestures();

                                showToast("Gesture सेव हो गया");
                            }
                        });
                    }

                    @Override
                    public void onGestureRecognized(
                            String name,
                            String action) {

                        runOnUiThread(() -> {

                            if (activityDestroyed || learning) {
                                return;
                            }

                            status.setText(
                                    "पहचाना गया: " + name +
                                    "\nAction: " + action
                            );

                            runRecognizedAction(name, action);
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (activityDestroyed) {
                                return;
                            }

                            cameraReady = false;
                            cameraStartRequested = false;

                            learning = false;

                            pendingGestureName = "";
                            pendingGestureAction = "";

                            status.setText("समस्या: " + message);
                        });
                    }
                }
        );
    }

    private void startCamera() {

        if (activityDestroyed || isFinishing() || isDestroyed()) {
            return;
        }

        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }

        if (cameraStartRequested) {
            return;
        }

        initializeCameraController();

        if (controller == null) {

            status.setText(
                    "Camera Controller शुरू नहीं हो पाया"
            );

            return;
        }

        cameraReady = false;
        cameraStartRequested = true;

        status.setText("Camera शुरू हो रहा है...");

        controller.start();
    }

    private void beginLearning() {

        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }

        if (controller == null) {
            initializeCameraController();
        }

        String name = gestureName.getText()
                .toString()
                .trim();

        String action = gestureAction.getText()
                .toString()
                .trim();

        if (name.isEmpty()) {
            gestureName.setError("Gesture का नाम लिखें");
            return;
        }

        if (action.isEmpty()) {
            gestureAction.setError("Action लिखें");
            return;
        }

        if (controller == null) {
            showToast("Camera Controller उपलब्ध नहीं है");
            return;
        }

        if (learning) {
            showToast("पहले से Learning चल रही है");
            return;
        }

        pendingGestureName = name;
        pendingGestureAction = action;

        if (!cameraReady) {

            status.setText(
                    "Camera तैयार हो रहा है। उसके बाद Learning शुरू होगी..."
            );

            startCamera();

            return;
        }

        startPendingLearning();
    }

    private void startPendingLearning() {

        if (activityDestroyed
                || !cameraReady
                || controller == null
                || pendingGestureName.isEmpty()
                || pendingGestureAction.isEmpty()) {

            return;
        }

        boolean started = controller.startLearning(
                pendingGestureName,
                pendingGestureAction
        );

        if (started) {

            learning = true;

            status.setText(
                    "Learning शुरू हो गई।\n" +
                    "अपना हाथ कैमरे के सामने रखें।"
            );

        } else {

            learning = false;

            pendingGestureName = "";
            pendingGestureAction = "";

            showToast(
                    "Learning शुरू नहीं हो पाई। दोबारा कोशिश करें।"
            );
        }
    }

    private void cancelLearning() {

        learning = false;

        pendingGestureName = "";
        pendingGestureAction = "";

        if (controller != null) {
            controller.cancelLearning();
        }

        status.setText("Gesture Learning रद्द कर दी गई");

        showToast("Learning रद्द हो गई");
    }

    private void runRecognizedAction(
            String name,
            String action) {

        if (name == null || action == null) {
            return;
        }

        String key = name.trim().toLowerCase();

        if (key.isEmpty()) {
            return;
        }

        long now = SystemClock.elapsedRealtime();

        Long lastTime = lastActionTimes.get(key);

        if (lastTime != null
                && now - lastTime < ACTION_COOLDOWN) {

            return;
        }

        boolean success = actionExecutor.execute(action);

        if (success) {

            lastActionTimes.put(key, now);

        } else {

            status.setText(
                    "Gesture पहचाना गया, लेकिन Action नहीं चला: " +
                    action
            );
        }
    }

    private void refreshSavedGestures() {

        if (savedList == null) {
            return;
        }

        savedList.removeAllViews();

        if (controller == null) {
            addEmptySavedMessage();
            return;
        }

        JSONArray gestures = controller.getSavedGestures();

        if (gestures == null || gestures.length() == 0) {
            addEmptySavedMessage();
            return;
        }

        for (int i = 0; i < gestures.length(); i++) {

            JSONObject item = gestures.optJSONObject(i);

            if (item == null) {
                continue;
            }

            String name = item.optString("name", "");
            String action = item.optString("action", "");

            if (name.trim().isEmpty()) {
                continue;
            }

            addSavedGestureItem(name, action);
        }
    }

    private void addEmptySavedMessage() {

        TextView empty = new TextView(this);

        empty.setText("अभी कोई Gesture सेव नहीं है।");
        empty.setTextSize(15);
        empty.setPadding(0, dp(8), 0, dp(8));

        savedList.addView(empty);
    }

    private void addSavedGestureItem(
            String name,
            String action) {

        LinearLayout itemLayout = new LinearLayout(this);

        itemLayout.setOrientation(LinearLayout.VERTICAL);

        itemLayout.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        TextView details = new TextView(this);

        details.setText(
                "Gesture: " + name +
                "\nAction: " + action
        );

        details.setTextSize(15);

        itemLayout.addView(details);

        Button removeButton = new Button(this);

        removeButton.setText("यह Gesture हटाएँ");

        removeButton.setOnClickListener(v -> {

            if (controller == null) {
                showToast(
                        "Gesture Controller उपलब्ध नहीं है"
                );
                return;
            }

            boolean removed = controller.removeGesture(name);

            if (removed) {

                showToast("Gesture हटा दिया गया");

                refreshSavedGestures();

            } else {

                showToast("Gesture हटाया नहीं जा सका");
            }
        });

        itemLayout.addView(removeButton);

        savedList.addView(itemLayout);

        View divider = new View(this);

        divider.setBackgroundColor(0xFFBBBBBB);

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        savedList.addView(divider, dividerParams);
    }

    private void showToast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (hasCameraPermission()) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {

        cameraReady = false;
        cameraStartRequested = false;

        if (controller != null) {

            controller.stop();
        }

        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == CAMERA_PERMISSION) {

            if (grantResults.length > 0
                    && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {

                initializeCameraController();

                startCamera();

                refreshSavedGestures();

            } else {

                cameraReady = false;
                cameraStartRequested = false;

                status.setText(
                        "Hand Gesture चलाने के लिए Camera Permission जरूरी है।"
                );

                showToast("Camera Permission नहीं मिली");
            }
        }
    }

    @Override
    protected void onDestroy() {

        activityDestroyed = true;

        cameraReady = false;
        cameraStartRequested = false;
        learning = false;

        pendingGestureName = "";
        pendingGestureAction = "";

        if (controller != null) {

            controller.shutdown();

            controller = null;
        }

        super.onDestroy();
    }
}
