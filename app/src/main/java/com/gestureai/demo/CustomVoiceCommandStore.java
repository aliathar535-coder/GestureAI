
package com.gestureai.demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class CustomVoiceCommandStore {

    private static final String PREF =
            "gesture_ai_custom_voice_commands";

    private static final String KEY =
            "commands";

    private final SharedPreferences preferences;

    public CustomVoiceCommandStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private String normalize(String value) {
        if (value == null) return "";

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0900-\\u097F]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private JSONArray readCommands() {
        try {
            String saved = preferences.getString(KEY, "[]");
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private boolean writeCommands(JSONArray array) {
        return preferences.edit()
                .putString(KEY, array.toString())
                .commit();
    }

    public synchronized boolean saveCommand(
            String phrase,
            String action) {

        String cleanPhrase = phrase == null ? "" : phrase.trim();
        String cleanAction = action == null ? "" : action.trim();

        String normalized = normalize(cleanPhrase);

        if (normalized.isEmpty() || cleanAction.isEmpty()) {
            return false;
        }

        JSONArray old = readCommands();
        JSONArray updated = new JSONArray();

        try {
            for (int i = 0; i < old.length(); i++) {
                try {
                    JSONObject item = old.getJSONObject(i);

                    String existing = normalize(
                            item.optString("phrase", "")
                    );

                    if (!existing.equals(normalized)) {
                        updated.put(item);
                    }
                } catch (Exception ignored) {
                }
            }

            JSONObject newItem = new JSONObject();
            newItem.put("phrase", cleanPhrase);
            newItem.put("action", cleanAction);

            updated.put(newItem);

            return writeCommands(updated);

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean removeCommand(String phrase) {

        String target = normalize(phrase);

        if (target.isEmpty()) return false;

        JSONArray old = readCommands();
        JSONArray updated = new JSONArray();
        boolean removed = false;

        try {
            for (int i = 0; i < old.length(); i++) {
                try {
                    JSONObject item = old.getJSONObject(i);

                    String existing = normalize(
                            item.optString("phrase", "")
                    );

                    if (existing.equals(target)) {
                        removed = true;
                    } else {
                        updated.put(item);
                    }

                } catch (Exception ignored) {
                }
            }

            if (!removed) return false;

            return writeCommands(updated);

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized String findAction(String spoken) {

        String input = normalize(spoken);

        if (input.isEmpty()) return null;

        JSONArray commands = readCommands();

        String bestAction = null;
        int bestLength = 0;

        for (int i = 0; i < commands.length(); i++) {

            try {
                JSONObject item = commands.getJSONObject(i);

                String phrase = normalize(
                        item.optString("phrase", "")
                );

                String action = item.optString("action", "").trim();

                if (phrase.isEmpty() || action.isEmpty()) {
                    continue;
                }

                boolean exact = input.equals(phrase);

                boolean phraseInside =
                        input.startsWith(phrase + " ")
                        || input.endsWith(" " + phrase)
                        || input.contains(" " + phrase + " ");

                if (exact || phraseInside) {

                    if (phrase.length() > bestLength) {
                        bestLength = phrase.length();
                        bestAction = action;
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return bestAction;
    }

    public synchronized JSONArray getCommandsJson() {

        JSONArray old = readCommands();
        JSONArray clean = new JSONArray();

        for (int i = 0; i < old.length(); i++) {
            try {
                JSONObject item = old.getJSONObject(i);

                String phrase = item.optString("phrase", "").trim();
                String action = item.optString("action", "").trim();

                if (phrase.isEmpty() || action.isEmpty()) {
                    continue;
                }

                JSONObject valid = new JSONObject();
                valid.put("phrase", phrase);
                valid.put("action", action);

                clean.put(valid);

            } catch (Exception ignored) {
            }
        }

        return clean;
    }
}
