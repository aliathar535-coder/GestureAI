
package com.gestureai.demo;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.Toast;
import android.content.ComponentName;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private WebView web;
    private WebViewAssetLoader assetLoader;

    private static final int CAMERA_PERMISSION_CODE = 7;
    private static final int AUDIO_PERMISSION_CODE = 8;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(10, 15, 30));
        getWindow().setNavigationBarColor(Color.rgb(10, 15, 30));

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(true);

        web.setBackgroundColor(Color.rgb(10, 15, 30));
        web.addJavascriptInterface(new AppBridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {

                runOnUiThread(() -> {

                    boolean allowed = true;
                    boolean needsCamera = false;
                    boolean needsAudio = false;

                    for (String resource : request.getResources()) {

                        if (resource.equals(
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {

                            needsCamera = true;

                            if (ContextCompat.checkSelfPermission(
                                    MainActivity.this,
                                    Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED) {
                                allowed = false;
                            }

                        } else if (resource.equals(
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {

                            needsAudio = true;

                            if (ContextCompat.checkSelfPermission(
                                    MainActivity.this,
                                    Manifest.permission.RECORD_AUDIO
                            ) != PackageManager.PERMISSION_GRANTED) {
                                allowed = false;
                            }
                        }
                    }

                    if (allowed) {
                        request.grant(request.getResources());
                    } else {
                        request.deny();

                        if (needsCamera && !hasCameraPermission()) {
                            requestCameraPermission();
                        }

                        if (needsAudio && !hasAudioPermission()) {
                            requestAudioPermission();
                        }
                    }
                });
            }
        });

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        web.loadUrl(
                "https://appassets.androidplatform.net/assets/index.html"
        );
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    CAMERA_PERMISSION_CODE
            );
        }
    }

    private void requestAudioPermission() {

        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    AUDIO_PERMISSION_CODE
            );
        }
    }

    private boolean isAccessibilityEnabled() {

        int enabled = 0;

        try {
            enabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Exception e) {
            return false;
        }

        if (enabled != 1) return false;

        String services = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (services == null) return false;

        ComponentName component = new ComponentName(
                this,
                GestureAccessibilityService.class
        );

        return services.contains(component.flattenToString());
    }

    public class AppBridge {

        @JavascriptInterface
        public String getInstalledApps() {

            JSONArray array = new JSONArray();

            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> apps =
                        getPackageManager().queryIntentActivities(intent, 0);

                Collections.sort(apps, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(getPackageManager()).toString()
                                .compareToIgnoreCase(
                                        b.loadLabel(getPackageManager()).toString()
                                );
                    }
                });

                for (ResolveInfo info : apps) {

                    JSONObject item = new JSONObject();

                    String label =
                            info.loadLabel(getPackageManager()).toString();

                    String packageName = info.activityInfo.packageName;

                    boolean system =
                            (info.activityInfo.applicationInfo.flags
                                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;

                    item.put("name", label);
                    item.put("package", packageName);
                    item.put("system", system);

                    array.put(item);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return array.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName) {

            runOnUiThread(() -> {

                try {
                    if (packageName == null ||
                            packageName.trim().isEmpty()) {

                        Toast.makeText(
                                MainActivity.this,
                                "App select nahi hua",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    Intent launchIntent =
                            getPackageManager().getLaunchIntentForPackage(
                                    packageName
                            );

                    if (launchIntent != null) {

                        launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                        );

                        startActivity(launchIntent);

                    } else {

                        Toast.makeText(
                                MainActivity.this,
                                "Is app ko open nahi kar sakte",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "App open nahi hua",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void requestPermissions() {
            runOnUiThread(() -> requestCameraPermission());
        }

        @JavascriptInterface
        public void requestCameraPermission() {
            runOnUiThread(() ->
                    MainActivity.this.requestCameraPermission()
            );
        }

        @JavascriptInterface
        public void requestAudioPermission() {
            runOnUiThread(() ->
                    MainActivity.this.requestAudioPermission()
            );
        }

        @JavascriptInterface
        public boolean isCameraAllowed() {
            return hasCameraPermission();
        }

        @JavascriptInterface
        public boolean isAudioAllowed() {
            return hasAudioPermission();
        }

        @JavascriptInterface
        public boolean isOverlayAllowed() {
            return Settings.canDrawOverlays(MainActivity.this);
        }

        @JavascriptInterface
        public void requestOverlayPermission() {

            runOnUiThread(() -> {

                if (!Settings.canDrawOverlays(MainActivity.this)) {

                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );

                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {

            runOnUiThread(() -> {

                startActivity(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                ));
            });
        }

        @JavascriptInterface
        public boolean isAccessibilityReady() {
            return isAccessibilityEnabled();
        }

        @JavascriptInterface
        public void startFloating() {

            runOnUiThread(() -> {

                if (!Settings.canDrawOverlays(MainActivity.this)) {

                    Toast.makeText(
                            MainActivity.this,
                            "Pehle Display over other apps permission dein",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                if (!isAccessibilityEnabled()) {

                    Toast.makeText(
                            MainActivity.this,
                            "Pehle Gesture AI Accessibility ON karein",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                try {
                    Intent intent = new Intent(
                            MainActivity.this,
                            FloatingOverlayService.class
                    );

                    startService(intent);

                    Toast.makeText(
                            MainActivity.this,
                            "Floating Control shuru ho gaya",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Floating Control shuru nahi hua",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void stopFloating() {

            runOnUiThread(() -> {

                stopService(new Intent(
                        MainActivity.this,
                        FloatingOverlayService.class
                ));

                Toast.makeText(
                        MainActivity.this,
                        "Floating Control band ho gaya",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }
    }

    @Override
    public void onBackPressed() {

        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (web != null) {
            web.removeJavascriptInterface("Android");
            web.destroy();
            web = null;
        }

        super.onDestroy();
    }
                             }
