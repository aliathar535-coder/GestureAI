
package com.gestureai.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class HandGestureActivity extends Activity {

    private static final int CAMERA_PERMISSION = 501;
    private static final long ACTION_COOLDOWN = 3000;

    private HandCameraController controller;
    private GestureActionExecutor actionExecutor;

    private PreviewView previewView;
    private TextView status;
    private EditText gestureName;
    private EditText gestureAction;
    private LinearLayout savedList;

    private boolean cameraStarted = false;

    private String lastExecutedAction = "";
    private long lastActionTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionExecutor = new GestureActionExecutor(this);

        createScreen();

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            startCamera();

        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION);
        }
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(0xFF101827);

        TextView heading = new TextView(this);
        heading.setText("GESTURE AI - HAND CONTROL");
        heading.setTextColor(0xFFFFFFFF);
        heading.setTextSize(20);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);

        previewView = new PreviewView(this);
        previewView.setImplementationMode(
                PreviewView.ImplementationMode.COMPATIBLE);

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 260);

        previewParams.setMargins(0, 12, 0, 12);
        root.addView(previewView, previewParams);

        status = new TextView(this);
        status.setText("Camera starting...");
        status.setTextColor(0xFF00E5FF);
        status.setTextSize(15);
        root.addView(status);

        gestureName = new EditText(this);
        gestureName.setSingleLine(true);
        gestureName.setHint("Apne ishare ka naam likhein");
        gestureName.setTextColor(0xFFFFFFFF);
        gestureName.setHintTextColor(0xFFAAAAAA);
        root.addView(gestureName);

        gestureAction = new EditText(this);
        gestureAction.setSingleLine(true);
        gestureAction.setHint("Is ishare ka action likhein");
        gestureAction.setTextColor(0xFFFFFFFF);
        gestureAction.setHintTextColor(0xFFAAAAAA);
        root.addView(gestureAction);

        Button learnButton = makeButton("LEARN & SAVE GESTURE");
        root.addView(learnButton);

        learnButton.setOnClickListener(v -> {

            if (controller == null || !cameraStarted) {
                showMessage("Camera abhi ready nahi hai");
                return;
            }

            String name =
                    gestureName.getText().toString().trim();

            String action =
                    gestureAction.getText().toString().trim();

            if (name.isEmpty() || action.isEmpty()) {
                showMessage("Naam aur action dono likhein");
                return;
            }

            if (controller.startLearning(name, action)) {
                status.setText(
                        "Ab camera ke saamne 5 baar ek hi ishara dikhayein");
            } else {
                showMessage("Learning shuru nahi hui");
            }
        });

        Button cancelButton = makeButton("CANCEL LEARNING");
        root.addView(cancelButton);

        cancelButton.setOnClickListener(v -> {
            if (controller != null) {
                controller.cancelLearning();
                status.setText("Learning cancelled");
            }
        });

        TextView savedHeading = new TextView(this);
        savedHeading.setText("SAVED GESTURES");
        savedHeading.setTextColor(0xFFFFFFFF);
        savedHeading.setTextSize(18);
        savedHeading.setPadding(0, 18, 0, 8);
        root.addView(savedHeading);

        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(savedList);

        root.addView(scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1));

        setContentView(root);

        refreshSavedList();
    }

    private Button makeButton(String title) {

        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        0xFF1769AA));

        return button;
    }

    private void startCamera() {

        controller = new HandCameraController(
                this,
                this,
                previewView,
                new HandCameraController.Listener() {

                    @Override
                    public void onLearningProgress(int count) {

                        if (count == 5) {
                            status.setText(
                                    "Gesture saved successfully!");
                            refreshSavedList();

                        } else if (count >= 0) {
                            status.setText(
                                    "Learning sample: " + count + "/5");
                        }
                    }

                    @Override
                    public void onGestureRecognized(
                            String name, String action) {

                        runRecognizedAction(name, action);
                    }

                    @Override
                    public void onError(String message) {
                        status.setText(message);
                    }
                });

        controller.start();
        cameraStarted = true;
    }

    private void runRecognizedAction(
            String name, String action) {

        long now = System.currentTimeMillis();

        if (action == null || action.trim().isEmpty()) {
            return;
        }

        if (action.equals(lastExecutedAction)
                && now - lastActionTime < ACTION_COOLDOWN) {
            return;
        }

        lastExecutedAction = action;
        lastActionTime = now;

        boolean success = actionExecutor.execute(action);

        if (success) {
            status.setText(
                    "Recognized: " + name +
                    "\nAction executed: " + action);
        } else {
            status.setText(
                    "Recognized: " + name +
                    "\nAction not completed: " + action);
        }
    }

    private void refreshSavedList() {

        if (savedList == null) return;

        savedList.removeAllViews();

        JSONArray data;

        if (controller != null) {
            data = controller.getSavedGestures();
        } else {
            data = loadSavedData();
        }

        for (int i = 0; i < data.length(); i++) {

            JSONObject item = data.optJSONObject(i);

            if (item == null) continue;

            String name = item.optString("name");
            String action = item.optString("action");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(8, 8, 8, 8);

            TextView details = new TextView(this);
            details.setText(name + "\nAction: " + action);
            details.setTextColor(0xFFFFFFFF);
            row.addView(details);

            Button remove = makeButton("REMOVE");
            row.addView(remove);

            remove.setOnClickListener(v -> {

                new android.app.AlertDialog.Builder(this)
                        .setTitle("Remove Gesture?")
                        .setMessage(
                                "Kya aap " + name +
                                " ko hatana chahte hain?")
                        .setNegativeButton("NO", null)
                        .setPositiveButton("YES",
                                (dialog, which) -> {

                            boolean removed = controller != null
                                    && controller.removeGesture(name);

                            if (removed) {
                                showMessage("Gesture removed");
                                refreshSavedList();
                            } else {
                                showMessage(
                                        "Gesture remove nahi hua");
                            }
                        })
                        .show();
            });

            savedList.addView(row);
        }
    }

    private JSONArray loadSavedData() {

        try {
            String json = getSharedPreferences(
                    "gesture_ai_data", MODE_PRIVATE)
                    .getString("saved_hand_gestures", "[]");

            return new JSONArray(json);

        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void showMessage(String message) {
        Toast.makeText(
                this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {

                startCamera();

            } else {
                status.setText("Camera permission required");
            }
        }
    }

    @Override
    protected void onDestroy() {

        cameraStarted = false;

        if (controller != null) {
            controller.shutdown();
            controller = null;
        }

        super.onDestroy();
    }
}
