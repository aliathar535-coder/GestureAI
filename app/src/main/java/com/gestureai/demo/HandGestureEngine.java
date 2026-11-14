
package com.gestureai.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HandGestureEngine {

    private static final String PREFS = "gesture_ai_data";
    private static final String DATA_KEY = "saved_hand_gestures";

    private static final int REQUIRED_SAMPLES = 5;

    private static final float MATCH_THRESHOLD = 0.12f;

    private final Context context;
    private final SharedPreferences preferences;

    private HandLandmarker landmarker;

    private String learningName;
    private String learningAction;

    private final List<float[]> learningSamples =
            new ArrayList<>();

    public HandGestureEngine(Context context) {

        this.context = context.getApplicationContext();

        preferences = this.context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    // ==============================
    // INITIALIZE MEDIAPIPE
    // ==============================

    public synchronized void initialize() throws Exception {

        if (landmarker != null) {
            return;
        }

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build();

        HandLandmarker.HandLandmarkerOptions options =
                HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.IMAGE)
                        .setNumHands(1)
                        .setMinHandDetectionConfidence(0.6f)
                        .setMinHandPresenceConfidence(0.6f)
                        .setMinTrackingConfidence(0.6f)
                        .build();

        landmarker = HandLandmarker.createFromOptions(
                context,
                options
        );
    }

    // ==============================
    // HAND LANDMARK DETECTION
    // ==============================

    private float[] getLandmarks(Bitmap bitmap) {

        if (landmarker == null || bitmap == null) {
            return null;
        }

        Bitmap safeBitmap = bitmap;
        Bitmap copiedBitmap = null;

        MPImage image = null;

        try {

            if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {

                copiedBitmap = bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

                safeBitmap = copiedBitmap;
            }

            if (safeBitmap == null) {
                return null;
            }

            image = new BitmapImageBuilder(
                    safeBitmap
            ).build();

            HandLandmarkerResult result =
                    landmarker.detect(image);

            if (result == null || result.landmarks().isEmpty()) {
                return null;
            }

            List<NormalizedLandmark> points =
                    result.landmarks().get(0);

            if (points == null || points.size() != 21) {
                return null;
            }

            float[] values = new float[63];

            for (int i = 0; i < 21; i++) {

                values[i * 3] = points.get(i).x();

                values[i * 3 + 1] = points.get(i).y();

                values[i * 3 + 2] = points.get(i).z();
            }

            return normalize(values);

        } finally {

            if (image != null) {
                image.close();
            }

            if (copiedBitmap != null
                    && !copiedBitmap.isRecycled()) {

                copiedBitmap.recycle();
            }
        }
    }

    // ==============================
    // NORMALIZE LANDMARKS
    // ==============================

    private float[] normalize(float[] values) {

        float originX = values[0];
        float originY = values[1];
        float originZ = values[2];

        float maxDistance = 0f;

        for (int i = 0; i < 21; i++) {

            values[i * 3] -= originX;

            values[i * 3 + 1] -= originY;

            values[i * 3 + 2] -= originZ;

            float x = values[i * 3];
            float y = values[i * 3 + 1];
            float z = values[i * 3 + 2];

            float distance = (float) Math.sqrt(
                    x * x + y * y + z * z
            );

            maxDistance = Math.max(
                    maxDistance,
                    distance
            );
        }

        if (maxDistance > 0f) {

            for (int i = 0; i < values.length; i++) {

                values[i] /= maxDistance;
            }
        }

        return values;
    }

    // ==============================
    // START LEARNING
    // ==============================

    public synchronized boolean startLearning(
            String name,
            String action) {

        if (landmarker == null
                || name == null
                || name.trim().isEmpty()
                || action == null
                || action.trim().isEmpty()) {

            return false;
        }

        learningName = name.trim();

        learningAction = action.trim();

        learningSamples.clear();

        return true;
    }

    // ==============================
    // ADD LEARNING FRAME
    // ==============================

    public synchronized int addLearningFrame(Bitmap bitmap) {

        if (learningName == null || landmarker == null) {
            return -1;
        }

        float[] landmarks = getLandmarks(bitmap);

        if (landmarks == null) {
            return -1;
        }

        learningSamples.add(landmarks);

        if (learningSamples.size() >= REQUIRED_SAMPLES) {

            boolean saved = saveGesture(
                    learningName,
                    learningAction,
                    learningSamples
            );

            if (!saved) {
                learningSamples.clear();
                learningName = null;
                learningAction = null;
                return -1;
            }

            learningSamples.clear();

            learningName = null;

            learningAction = null;

            return REQUIRED_SAMPLES;
        }

        return learningSamples.size();
    }

    // ==============================
    // CANCEL LEARNING
    // ==============================

    public synchronized void cancelLearning() {

        learningSamples.clear();

        learningName = null;

        learningAction = null;
    }

    // ==============================
    // SAVE GESTURE
    // ==============================

    private boolean saveGesture(
            String name,
            String action,
            List<float[]> samples) {

        try {

            JSONArray oldData = getSavedData();

            JSONArray all = new JSONArray();

            // Same name ka purana Gesture replace hoga.
            for (int i = 0; i < oldData.length(); i++) {

                JSONObject oldItem =
                        oldData.optJSONObject(i);

                if (oldItem == null) {
                    continue;
                }

                String oldName = oldItem.optString(
                        "name",
                        ""
                );

                if (!oldName.equalsIgnoreCase(name)) {
                    all.put(oldItem);
                }
            }

            JSONObject gesture = new JSONObject();

            gesture.put("name", name);

            gesture.put("action", action);

            JSONArray sampleArray = new JSONArray();

            for (float[] sample : samples) {

                JSONArray points = new JSONArray();

                for (float value : sample) {

                    points.put(value);
                }

                sampleArray.put(points);
            }

            gesture.put("samples", sampleArray);

            all.put(gesture);

            boolean success = preferences.edit()
                    .putString(DATA_KEY, all.toString())
                    .commit();

            return success;

        } catch (Exception e) {

            return false;
        }
    }

    // ==============================
    // RECOGNIZE GESTURE
    // ==============================

    public synchronized JSONObject recognize(Bitmap bitmap) {

        if (landmarker == null || bitmap == null) {
            return null;
        }

        float[] current = getLandmarks(bitmap);

        if (current == null) {
            return null;
        }

        JSONArray all = getSavedData();

        JSONObject bestMatch = null;

        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < all.length(); i++) {

            try {

                JSONObject gesture = all.getJSONObject(i);

                JSONArray samples = gesture.getJSONArray(
                        "samples"
                );

                float totalDistance = 0f;

                int validSamples = 0;

                for (int j = 0; j < samples.length(); j++) {

                    JSONArray points = samples.getJSONArray(j);

                    if (points.length() != 63) {
                        continue;
                    }

                    float distance = 0f;

                    for (int k = 0; k < 63; k++) {

                        float difference =
                                current[k] -
                                (float) points.getDouble(k);

                        distance += difference * difference;
                    }

                    totalDistance += (float) Math.sqrt(
                            distance / 63f
                    );

                    validSamples++;
                }

                if (validSamples == 0) {
                    continue;
                }

                float average =
                        totalDistance / validSamples;

                if (average < bestDistance) {

                    bestDistance = average;

                    bestMatch = gesture;
                }

            } catch (Exception ignored) {
            }
        }

        if (bestMatch != null
                && bestDistance < MATCH_THRESHOLD) {

            try {

                JSONObject match = new JSONObject();

                match.put(
                        "name",
                        bestMatch.getString("name")
                );

                match.put(
                        "action",
                        bestMatch.getString("action")
                );

                match.put(
                        "distance",
                        bestDistance
                );

                return match;

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ==============================
    // GET SAVED GESTURES
    // ==============================

    public synchronized JSONArray getSavedData() {

        String saved = preferences.getString(
                DATA_KEY,
                "[]"
        );

        try {

            return new JSONArray(saved);

        } catch (Exception e) {

            return new JSONArray();
        }
    }

    // ==============================
    // REMOVE GESTURE
    // ==============================

    public synchronized boolean removeGesture(String name) {

        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        JSONArray oldData = getSavedData();

        JSONArray newData = new JSONArray();

        boolean removed = false;

        for (int i = 0; i < oldData.length(); i++) {

            JSONObject item = oldData.optJSONObject(i);

            if (item == null) {
                continue;
            }

            String savedName = item.optString(
                    "name",
                    ""
            );

            if (savedName.equalsIgnoreCase(name.trim())) {

                removed = true;

            } else {

                newData.put(item);
            }
        }

        if (removed) {

            return preferences.edit()
                    .putString(DATA_KEY, newData.toString())
                    .commit();
        }

        return false;
    }

    // ==============================
    // CLOSE MEDIAPIPE
    // ==============================

    public synchronized void close() {

        cancelLearning();

        if (landmarker != null) {

            try {
                landmarker.close();
            } catch (Exception ignored) {
            }

            landmarker = null;
        }
    }
}
