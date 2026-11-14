
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
                PREFS, Context.MODE_PRIVATE);
    }

    public void initialize() throws Exception {

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
                context, options);
    }

    private float[] getLandmarks(Bitmap bitmap) {

        if (landmarker == null || bitmap == null) {
            return null;
        }

        Bitmap safeBitmap = bitmap;

        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            safeBitmap = bitmap.copy(
                    Bitmap.Config.ARGB_8888, false);
        }

        if (safeBitmap == null) {
            return null;
        }

        MPImage image =
                new BitmapImageBuilder(safeBitmap).build();

        HandLandmarkerResult result =
                landmarker.detect(image);

        if (result.landmarks().isEmpty()) {
            return null;
        }

        List<NormalizedLandmark> points =
                result.landmarks().get(0);

        if (points.size() != 21) {
            return null;
        }

        float[] values = new float[63];

        for (int i = 0; i < 21; i++) {
            values[i * 3] = points.get(i).x();
            values[i * 3 + 1] = points.get(i).y();
            values[i * 3 + 2] = points.get(i).z();
        }

        return normalize(values);
    }

    private float[] normalize(float[] values) {

        float originX = values[0];
        float originY = values[1];
        float originZ = values[2];

        float maxDistance = 0f;

        for (int i = 0; i < 21; i++) {

            values[i * 3] -= originX;
            values[i * 3 + 1] -= originY;
            values[i * 3 + 2] -= originZ;

            float distance = (float) Math.sqrt(
                    values[i * 3] * values[i * 3] +
                    values[i * 3 + 1] * values[i * 3 + 1] +
                    values[i * 3 + 2] * values[i * 3 + 2]);

            maxDistance = Math.max(maxDistance, distance);
        }

        if (maxDistance > 0) {
            for (int i = 0; i < values.length; i++) {
                values[i] /= maxDistance;
            }
        }

        return values;
    }

    public synchronized boolean startLearning(
            String name, String action) {

        if (name == null || name.trim().isEmpty()
                || action == null || action.trim().isEmpty()) {
            return false;
        }

        learningName = name.trim();
        learningAction = action.trim();
        learningSamples.clear();

        return true;
    }

    public synchronized int addLearningFrame(Bitmap bitmap) {

        if (learningName == null) {
            return -1;
        }

        float[] landmarks = getLandmarks(bitmap);

        if (landmarks == null) {
            return -1;
        }

        learningSamples.add(landmarks);

        if (learningSamples.size() >= REQUIRED_SAMPLES) {

            saveGesture(
                    learningName,
                    learningAction,
                    learningSamples);

            learningSamples.clear();
            learningName = null;
            learningAction = null;

            return REQUIRED_SAMPLES;
        }

        return learningSamples.size();
    }

    public synchronized void cancelLearning() {
        learningSamples.clear();
        learningName = null;
        learningAction = null;
    }

    private void saveGesture(
            String name,
            String action,
            List<float[]> samples) {

        try {

            JSONArray all = getSavedData();

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

            preferences.edit()
                    .putString(DATA_KEY, all.toString())
                    .apply();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Gesture save failed", e);
        }
    }

    public synchronized JSONObject recognize(Bitmap bitmap) {

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
                JSONArray samples = gesture.getJSONArray("samples");

                float totalDistance = 0;
                int validSamples = 0;

                for (int j = 0; j < samples.length(); j++) {

                    JSONArray points = samples.getJSONArray(j);

                    if (points.length() != 63) continue;

                    float distance = 0;

                    for (int k = 0; k < 63; k++) {
                        float difference =
                                current[k] - (float) points.getDouble(k);

                        distance += difference * difference;
                    }

                    totalDistance +=
                            (float) Math.sqrt(distance / 63);

                    validSamples++;
                }

                if (validSamples == 0) continue;

                float average =
                        totalDistance / validSamples;

                if (average < bestDistance) {
                    bestDistance = average;
                    bestMatch = gesture;
                }

            } catch (Exception ignored) {
            }
        }

        if (bestMatch != null && bestDistance < 0.12f) {

            try {
                JSONObject match = new JSONObject();
                match.put("name", bestMatch.getString("name"));
                match.put("action", bestMatch.getString("action"));
                match.put("distance", bestDistance);
                return match;
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public synchronized JSONArray getSavedData() {

        String saved = preferences.getString(DATA_KEY, "[]");

        try {
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public synchronized boolean removeGesture(String name) {

        JSONArray oldData = getSavedData();
        JSONArray newData = new JSONArray();

        boolean removed = false;

        for (int i = 0; i < oldData.length(); i++) {

            JSONObject item = oldData.optJSONObject(i);

            if (item == null) continue;

            if (item.optString("name").equals(name)) {
                removed = true;
            } else {
                newData.put(item);
            }
        }

        if (removed) {
            preferences.edit()
                    .putString(DATA_KEY, newData.toString())
                    .apply();
        }

        return removed;
    }

    public void close() {

        if (landmarker != null) {
            landmarker.close();
            landmarker = null;
        }
    }
                  }
