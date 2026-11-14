
package com.gestureai.demo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

public class GestureActionExecutor {

    private final Context context;

    private static boolean torchOn = false;

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
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);

            case "home":
            case "ghar":
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);

            case "recent":
            case "recent apps":
                return runGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);

            case "scroll up":
            case "upar scroll":
                return GestureAccessibilityService.scroll(true);

            case "scroll down":
            case "neeche scroll":
                return GestureAccessibilityService.scroll(false);

            case "flashlight":
            case "torch":
            case "torch on":
            case "torch off":
                return toggleTorch();

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
                AudioManager.FLAG_SHOW_UI
            );
        }
    }

    private boolean runGlobalAction(int action) {

        return GestureAccessibilityService.runGlobalAction(action);
    }

    private boolean toggleTorch() {

        CameraManager cameraManager =
            (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);

        if (cameraManager == null) return false;

        try {
            for (String cameraId : cameraManager.getCameraIdList()) {

                CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraId);

                Boolean hasFlash = characteristics.get(
                    CameraCharacteristics.FLASH_INFO_AVAILABLE);

                if (Boolean.TRUE.equals(hasFlash)) {

                    boolean newState = !torchOn;

                    cameraManager.setTorchMode(cameraId, newState);

                    torchOn = newState;

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
            .trim();

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

        Intent launcher = context.getPackageManager()
            .getLaunchIntentForPackage(packageName);

        if (launcher == null) return false;

        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launcher);

        return true;
    }
}
