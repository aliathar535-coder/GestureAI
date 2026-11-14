
package com.gestureai.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import java.util.Locale;

public class GestureActionExecutor {

    private final Context context;
    private static boolean torchOn = false;

    public GestureActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean execute(String action) {

        if (action == null || action.trim().isEmpty()) {
            return false;
        }

        String command = action.trim().toLowerCase(Locale.ROOT);

        switch (command) {

            case "volume up":
            case "volume badhao":
            case "आवाज बढ़ाओ":
                return adjustVolume(AudioManager.ADJUST_RAISE);

            case "volume down":
            case "volume ghatao":
            case "आवाज कम करो":
                return adjustVolume(AudioManager.ADJUST_LOWER);

            case "mute":
            case "volume mute":
            case "आवाज बंद":
                return adjustVolume(AudioManager.ADJUST_MUTE);

            case "back":
            case "wapas":
            case "पीछे":
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                );

            case "home":
            case "ghar":
            case "होम":
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                );

            case "recent":
            case "recent apps":
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                );

            case "scroll up":
            case "upar scroll":
            case "ऊपर स्क्रॉल":
                return GestureAccessibilityService.scroll(true);

            case "scroll down":
            case "neeche scroll":
            case "नीचे स्क्रॉल":
                return GestureAccessibilityService.scroll(false);

            case "torch on":
            case "flashlight on":
            case "torch chalu":
            case "टॉर्च चालू":
                return setTorch(true);

            case "torch off":
            case "flashlight off":
            case "torch band":
            case "टॉर्च बंद":
                return setTorch(false);

            case "torch":
            case "flashlight":
                return setTorch(!torchOn);

            default:
                return openApp(command);
        }
    }

    private boolean adjustVolume(int direction) {

        try {
            AudioManager audio = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);

            if (audio == null) return false;

            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            );

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean runGlobalAction(int action) {
        return GestureAccessibilityService.runGlobalAction(action);
    }

    private boolean setTorch(boolean enabled) {

        CameraManager manager = (CameraManager)
            context.getSystemService(Context.CAMERA_SERVICE);

        if (manager == null) return false;

        try {
            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(cameraId);

                Boolean hasFlash = characteristics.get(
                    CameraCharacteristics.FLASH_INFO_AVAILABLE
                );

                if (Boolean.TRUE.equals(hasFlash)) {

                    manager.setTorchMode(cameraId, enabled);
                    torchOn = enabled;

                    return true;
                }
            }

        } catch (CameraAccessException |
                 SecurityException |
                 IllegalArgumentException e) {
            return false;
        }

        return false;
    }

    private boolean openApp(String command) {

        String appName = command
            .replace(" kholo", "")
            .replace(" kholna", "")
            .replace(" open", "")
            .replace(" start", "")
            .replace(" खोलो", "")
            .replace(" खोलना", "")
            .trim();

        if (appName.isEmpty()) return false;

        String packageName;

        switch (appName) {

            case "youtube":
                packageName = "com.google.android.youtube";
                break;

            case "mx player":
            case "mxplayer":
                packageName = "com.mxtech.videoplayer.ad";
                break;

            case "whatsapp":
                packageName = "com.whatsapp";
                break;

            case "instagram":
                packageName = "com.instagram.android";
                break;

            case "facebook":
                packageName = "com.facebook.katana";
                break;

            case "chrome":
                packageName = "com.android.chrome";
                break;

            default:
                packageName = appName;
                break;
        }

        try {
            Intent launcher = context.getPackageManager()
                .getLaunchIntentForPackage(packageName);

            if (launcher == null) return false;

            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launcher);

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
