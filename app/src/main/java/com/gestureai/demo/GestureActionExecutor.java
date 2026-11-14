
package com.gestureai.demo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;

public class GestureActionExecutor {

    private final Context context;

    public GestureActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean execute(String action) {

        if (action == null) return false;

        String command = action.trim().toLowerCase();

        switch (command) {

            case "volume up":
            case "volume badhao":
                adjustVolume(AudioManager.ADJUST_RAISE);
                return true;

            case "volume down":
            case "volume ghatao":
                adjustVolume(AudioManager.ADJUST_LOWER);
                return true;

            case "mute":
            case "volume mute":
                adjustVolume(AudioManager.ADJUST_MUTE);
                return true;

            case "back":
            case "wapas":
                return performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);

            case "home":
            case "ghar":
                return performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);

            case "recent":
            case "recent apps":
                return performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);

            case "scroll up":
            case "upar scroll":
                return performScroll(true);

            case "scroll down":
            case "neeche scroll":
                return performScroll(false);

            case "flashlight":
            case "torch":
                openFlashlightSettings();
                return true;

            default:
                return openApp(command);
        }
    }

    private void adjustVolume(int direction) {

        AudioManager audio = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);

        if (audio != null) {
            audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI);
        }
    }

    private boolean performGlobalAction(int action) {
        return GestureAccessibilityService.performGlobalAction(action);
    }

    private boolean performScroll(boolean up) {
        return GestureAccessibilityService.scroll(up);
    }

    private void openFlashlightSettings() {

        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private boolean openApp(String appName) {

        Intent launcher = context.getPackageManager()
                .getLaunchIntentForPackage(appName);

        if (launcher == null) {
            return false;
        }

        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launcher);

        return true;
    }
}
