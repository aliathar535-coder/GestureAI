
package com.gestureai.demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class CustomVoiceCommandStore {

    private static final String PREF_NAME =
            "gesture_ai_custom_voice_commands";

    private static final String KEY_COMMANDS = "commands";

    private final SharedPreferences prefs;

    public CustomVoiceCommandStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );
    }

    private String normalize(String value) {
        if (value == null) return "";

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u0900-\\u097F]", "");
    }

    private JSONArray readCommands() {
        String saved = prefs.getString(KEY_COMMANDS, "[]");

        try {
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void writeCommands(JSONArray commands) {
        prefs.edit()
                .putString(KEY_COMMANDS, commands.toString())
                .apply();
    }

    public synchronized boolean saveCommand(
            String phrase,
            String action
    ) {
        if (phrase == null || action == null) {
            return false;
        }

        phrase = phrase.trim();
        action = action.trim();

        if (phrase.isEmpty() || action.isEmpty()) {
            return false;
        }

        String newPhrase = normalize(phrase);

        if (newPhrase.isEmpty()) {
            return false;
        }

        JSONArray oldCommands = readCommands();
        JSONArray updated = new JSONArray();

        try {
            for (int i = 0; i < oldCommands.length(); i++) {

                JSONObject item = oldCommands.getJSONObject(i);

                String existingPhrase =
                        normalize(item.optString("phrase"));

                if (!existingPhrase.equals(newPhrase)) {
                    updated.put(item);
                }
            }

            JSONObject command = new JSONObject();

            command.put("phrase", phrase);
            command.put("action", action);

            updated.put(command);

            writeCommands(updated);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean removeCommand(String phrase) {

        if (phrase == null || phrase.trim().isEmpty()) {
            return false;
        }

        String target = normalize(phrase);

        JSONArray oldCommands = readCommands();
        JSONArray updated = new JSONArray();

        boolean removed = false;

        try {
            for (int i = 0; i < oldCommands.length(); i++) {

                JSONObject item = oldCommands.getJSONObject(i);

                String existingPhrase =
                        normalize(item.optString("phrase"));

                if (existingPhrase.equals(target)) {
                    removed = true;
                } else {
                    updated.put(item);
                }
            }

            if (removed) {
                writeCommands(updated);
            }

            return removed;

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized String findAction(String spokenCommand) {

        String spoken = normalize(spokenCommand);

        if (spoken.isEmpty()) {
            return null;
        }

        JSONArray commands = readCommands();

        try {

            // पहले पूरा वाक्य मिलाएँ
            for (int i = 0; i < commands.length(); i++) {

                JSONObject item = commands.getJSONObject(i);

                String phrase =
                        normalize(item.optString("phrase"));

                if (!phrase.isEmpty() && spoken.equals(phrase)) {
                    return item.optString("action", null);
                }
            }

            // अब सबसे लंबा मिलता-जुलता कमांड खोजें
            String bestAction = null;
            int bestLength = 0;

            for (int i = 0; i < commands.length(); i++) {

                JSONObject item = commands.getJSONObject(i);

                String phrase =
                        normalize(item.optString("phrase"));

                if (!phrase.isEmpty()
                        && spoken.contains(phrase)
                        && phrase.length() > bestLength) {

                    bestLength = phrase.length();

                    bestAction = item.optString("action", null);
                }
            }

            return bestAction;

        } catch (Exception e) {
            return null;
        }
    }

    public synchronized String getCommandsJson() {
        return readCommands().toString();
    }
}
