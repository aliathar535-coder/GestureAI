
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
import java.util.Locale;

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

    public synchronized void initialize() throws Exception {

        close();

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

    private float[] getLandmarks(Bitmap bitmap) {

        if (landmarker == null || bitmap == null
                || bitmap.isRecycled()) {
            return null;
        }

        Bitmap safeBitmap = bitmap;
        boolean copied = false;

        try {

            if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {

                safeBitmap = bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

                copied = true;
            }

            if (safeBitmap == null) return null;

            MPImage image =
                    new BitmapImageBuilder(safeBitmap).build();

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

        } catch (Exception e) {
            return null;

        } finally {

            if (copied && safeBitmap != null
                    && !safeBitmap.isRecycled()) {
                safeBitmap.recycle();
            }
        }
    }

    private float[] normalize(float[] values) {

        if (values == null || values.length != 63) {
            return null;
        }

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

            maxDistance = Math.max(maxDistance, distance);
        }

        if (maxDistance <= 0f) return null;

        for (int i = 0; i < values.length; i++) {
            values[i] /= maxDistance;
        }

        return values;
    }

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

            learningSamples.clear();
            learningName = null;
            learningAction = null;

            return saved ? REQUIRED_SAMPLES : -1;
        }

        return learningSamples.size();
    }

    public synchronized void cancelLearning() {

        learningSamples.clear();
        learningName = null;
        learningAction = null;
    }

    private boolean saveGesture(
            String name,
            String action,
            List<float[]> samples) {

        try {

            JSONArray all = getSavedData();
            JSONArray updated = new JSONArray();

            String target = normalizeName(name);

            // पुराने नाम वाले Gesture को हटाएँ
            for (int i = 0; i < all.length(); i++) {

                JSONObject old = all.optJSONObject(i);

                if (old == null) continue;

                String oldName = normalizeName(
                        old.optString("name", "")
                );

                if (!oldName.equals(target)) {
                    updated.put(old);
                }
            }

            JSONObject gesture = new JSONObject();

            gesture.put("name", name.trim());
            gesture.put("action", action.trim());

            JSONArray sampleArray = new JSONArray();

            for (float[] sample : samples) {

                if (sample == null || sample.length != 63) {
                    continue;
                }

                JSONArray points = new JSONArray();

                for (float value : sample) {
                    points.put(value);
                }

                sampleArray.put(points);
            }

            if (sampleArray.length() == 0) {
                return false;
            }

            gesture.put("samples", sampleArray);
            updated.put(gesture);

            return preferences.edit()
                    .putString(DATA_KEY, updated.toString())
                    .commit();

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized JSONObject recognize(Bitmap bitmap) {

        if (landmarker == null) return null;

        float[] current = getLandmarks(bitmap);

        if (current == null) return null;

        JSONArray all = getSavedData();

        JSONObject bestMatch = null;
        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < all.length(); i++) {

            try {

                JSONObject gesture = all.optJSONObject(i);

                if (gesture == null) continue;

                JSONArray samples =
                        gesture.optJSONArray("samples");

                if (samples == null) continue;

                float totalDistance = 0f;
                int validSamples = 0;

                for (int j = 0; j < samples.length(); j++) {

                    JSONArray points = samples.optJSONArray(j);

                    if (points == null || points.length() != 63) {
                        continue;
                    }

                    float distance = 0f;
                    boolean valid = true;

                    for (int k = 0; k < 63; k++) {

                        double stored = points.optDouble(k,
                                Double.NaN);

                        if (Double.isNaN(stored)
                                || Double.isInfinite(stored)) {
                            valid = false;
                            break;
                        }

                        float difference =
                                current[k] - (float) stored;

                        distance += difference * difference;
                    }

                    if (!valid) continue;

                    totalDistance +=
                            (float) Math.sqrt(distance / 63f);

                    validSamples++;
                }

                if (validSamples == 0) continue;

                float average = totalDistance / validSamples;

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

                match.put("name", bestMatch.optString("name"));
                match.put("action", bestMatch.optString("action"));
                match.put("distance", bestDistance);

                return match;

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String normalizeName(String name) {

        if (name == null) return "";

        return name.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized JSONArray getSavedData() {

        try {

            String saved = preferences.getString(
                    DATA_KEY,
                    "[]"
            );

            return new JSONArray(saved);

        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public synchronized boolean removeGesture(String name) {

        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        JSONArray oldData = getSavedData();
        JSONArray newData = new JSONArray();

        boolean removed = false;

        String target = normalizeName(name);

        for (int i = 0; i < oldData.length(); i++) {

            JSONObject item = oldData.optJSONObject(i);

            if (item == null) continue;

            String existing = normalizeName(
                    item.optString("name", "")
            );

            if (existing.equals(target)) {
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

    public synchronized void close() {

        if (landmarker != null) {

            try {
                landmarker.close();
            } catch (Exception ignored) {
            }

            landmarker = null;
        }

        learningSamples.clear();
        learningName = null;
        learningAction = null;
    }
}
