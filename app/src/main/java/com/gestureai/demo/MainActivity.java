
package com.gestureai.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 101;
    private static final int REQUEST_AUDIO = 102;
    private static final int REQUEST_NOTIFICATION = 103;

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    private PermissionRequest pendingWebPermission;
    private boolean pendingCameraPermission;
    private boolean pendingAudioPermission;

    private boolean pageLoaded = false;

    private static final String APP_URL =
            "https://appassets.androidplatform.net/assets/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createWebView();

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {

                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                }
        );

        requestNotificationPermissionIfNeeded();
    }

    private void createWebView() {

        webView = new WebView(this);

        setContentView(webView);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler(
                        "/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build();

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(
                        request.getUrl()
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                Uri uri = request.getUrl();

                if (uri == null) {
                    return true;
                }

                String host = uri.getHost();

                if ("appassets.androidplatform.net".equals(host)
                        && "https".equals(uri.getScheme())) {
                    return false;
                }

                return true;
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);

                if (APP_URL.equals(url)) {
                    pageLoaded = true;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                    PermissionRequest request
            ) {

                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(
                    PermissionRequest request
            ) {

                if (pendingWebPermission == request) {
                    pendingWebPermission = null;
                    pendingCameraPermission = false;
                    pendingAudioPermission = false;
                }
            }
        });

        webView.addJavascriptInterface(
                new AppBridge(this),
                "Android"
        );

        webView.loadUrl(APP_URL);
    }

    private void handleWebPermissionRequest(
            PermissionRequest request
    ) {

        if (request == null || isFinishing() || isDestroyed()) {
            return;
        }

        Uri origin = request.getOrigin();

        if (origin == null
                || !"https".equals(origin.getScheme())
                || !"appassets.androidplatform.net".equals(origin.getHost())) {

            request.deny();
            return;
        }

        boolean needsCamera = false;
        boolean needsAudio = false;

        for (String resource : request.getResources()) {

            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                needsCamera = true;
            }

            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                needsAudio = true;
            }
        }

        if (!needsCamera && !needsAudio) {
            request.deny();
            return;
        }

        pendingWebPermission = request;

        pendingCameraPermission =
                needsCamera && !hasCameraPermission();

        pendingAudioPermission =
                needsAudio && !hasAudioPermission();

        if (pendingCameraPermission) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA
            );
            return;
        }

        if (pendingAudioPermission) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO
            );
            return;
        }

        grantWebPermissionIfReady();
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

    private void grantWebPermissionIfReady() {

        PermissionRequest request = pendingWebPermission;

        if (request == null) {
            return;
        }

        if (request.getOrigin() == null
                || !"appassets.androidplatform.net".equals(
                        request.getOrigin().getHost())) {
            request.deny();
            pendingWebPermission = null;
            return;
        }

        if (pendingCameraPermission && !hasCameraPermission()) {
            request.deny();
            pendingWebPermission = null;
            return;
        }

        if (pendingAudioPermission && !hasAudioPermission()) {
            request.deny();
            pendingWebPermission = null;
            return;
        }

        JSONArray resources = new JSONArray();

        for (String resource : request.getResources()) {

            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && hasCameraPermission()) {
                resources.put(resource);
            }

            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && hasAudioPermission()) {
                resources.put(resource);
            }
        }

        String[] granted = new String[resources.length()];

        for (int i = 0; i < resources.length(); i++) {
            granted[i] = resources.optString(i);
        }

        pendingWebPermission = null;
        pendingCameraPermission = false;
        pendingAudioPermission = false;

        if (granted.length > 0) {
            request.grant(granted);
        } else {
            request.deny();
        }
    }

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    REQUEST_NOTIFICATION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQUEST_CAMERA
                || requestCode == REQUEST_AUDIO) {

            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (requestCode == REQUEST_CAMERA) {
                pendingCameraPermission = !granted;
            }

            if (requestCode == REQUEST_AUDIO) {
                pendingAudioPermission = !granted;
            }

            if (pendingWebPermission != null) {
                grantWebPermissionIfReady();
            }

            if (!granted) {
                showToast("Permission नहीं मिली");
            }

        } else if (requestCode == REQUEST_NOTIFICATION) {

            // Notification permission is optional for opening the app.
        }
    }

    private void showToast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    public class AppBridge {

        private final Context context;
        private final CustomVoiceCommandStore commandStore;

        AppBridge(Context context) {
            this.context = context.getApplicationContext();
            this.commandStore = new CustomVoiceCommandStore(context);
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
        public void requestCameraPermission() {

            runOnUiThread(() -> {

                if (!hasCameraPermission()) {
                    MainActivity.this.requestPermissions(
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA
                    );
                } else {
                    showToast("Camera Permission पहले से मिली हुई है");
                }
            });
        }

        @JavascriptInterface
        public void requestAudioPermission() {

            runOnUiThread(() -> {

                if (!hasAudioPermission()) {
                    MainActivity.this.requestPermissions(
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_AUDIO
                    );
                } else {
                    showToast("Microphone Permission पहले से मिली हुई है");
                }
            });
        }

        @JavascriptInterface
        public void openHandGesture() {

            runOnUiThread(() -> {

                Intent intent = new Intent(
                        MainActivity.this,
                        HandGestureActivity.class
                );

                startActivity(intent);
            });
        }

        @JavascriptInterface
        public String getCustomVoiceCommands() {
            return commandStore.getCommandsJson();
        }

        @JavascriptInterface
        public boolean saveCustomVoiceCommand(
                String phrase,
                String action
        ) {

            if (phrase == null || action == null
                    || phrase.trim().isEmpty()
                    || action.trim().isEmpty()) {
                return false;
            }

            return commandStore.saveCommand(
                    phrase.trim(),
                    action.trim()
            );
        }

        @JavascriptInterface
        public boolean removeCustomVoiceCommand(String phrase) {

            if (phrase == null || phrase.trim().isEmpty()) {
                return false;
            }

            return commandStore.removeCommand(phrase.trim());
        }

        @JavascriptInterface
        public String getInstalledApps() {

            JSONArray apps = new JSONArray();

            try {

                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);

                PackageManager pm = getPackageManager();

                List<ResolveInfo> results =
                        pm.queryIntentActivities(intent, 0);

                for (ResolveInfo info : results) {

                    if (info.activityInfo == null) {
                        continue;
                    }

                    JSONObject item = new JSONObject();

                    item.put(
                            "name",
                            info.loadLabel(pm).toString()
                    );

                    item.put(
                            "packageName",
                            info.activityInfo.packageName
                    );

                    apps.put(item);
                }

            } catch (Exception e) {
                return "[]";
            }

            return apps.toString();
            }
        
        @JavascriptInterface
        public void openAccessibilitySettings() {

            runOnUiThread(() -> {

                try {
                    Intent intent = new Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                    );

                    startActivity(intent);

                } catch (Exception e) {
                    showToast("Accessibility Settings नहीं खुलीं");
                }
            });
        }

        @JavascriptInterface
        public boolean isAccessibilityEnabled() {

            try {

                String enabledServices = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

                return enabledServices != null
                        && enabledServices.toLowerCase().contains(
                                getPackageName().toLowerCase()
                        );

            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void startVoiceControl() {

            runOnUiThread(() -> {

                if (!hasAudioPermission()) {
                    MainActivity.this.requestPermissions(
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_AUDIO
                    );

                    showToast("पहले Microphone Permission दें");
                    return;
                }

                try {

                    Intent intent = new Intent(
                            MainActivity.this,
                            VoiceForegroundService.class
                    );

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }

                    showToast("Voice Control शुरू करने का अनुरोध भेज दिया");

                } catch (Exception e) {
                    showToast("Voice Control शुरू नहीं हो पाया");
                }
            });
        }

        @JavascriptInterface
        public void stopVoiceControl() {

            runOnUiThread(() -> {

                try {

                    Intent intent = new Intent(
                            MainActivity.this,
                            VoiceForegroundService.class
                    );

                    stopService(intent);

                    showToast("Voice Control बंद कर दिया");

                } catch (Exception e) {
                    showToast("Voice Control बंद नहीं हो पाया");
                }
            });
        }

        @JavascriptInterface
        public void startFloating() {

            runOnUiThread(() -> {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(MainActivity.this)) {

                    showToast("पहले Floating Window Permission दें");
                    openOverlayPermissionSettings();
                    return;
                }

                try {

                    Intent intent = new Intent(
                            MainActivity.this,
                            FloatingOverlayService.class
                    );

                    startService(intent);

                    showToast("Floating Controls शुरू करने का अनुरोध भेज दिया");

                } catch (Exception e) {
                    showToast("Floating Controls शुरू नहीं हो पाए");
                }
            });
        }

        @JavascriptInterface
        public void stopFloating() {

            runOnUiThread(() -> {

                try {

                    Intent intent = new Intent(
                            MainActivity.this,
                            FloatingOverlayService.class
                    );

                    stopService(intent);

                } catch (Exception e) {
                    showToast("Floating Controls बंद नहीं हो पाए");
                }
            });
        }

        @JavascriptInterface
        public void requestOverlayPermission() {

            runOnUiThread(() -> {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(MainActivity.this)) {

                    openOverlayPermissionSettings();

                } else {
                    showToast("Floating Window Permission पहले से मिली हुई है");
                }
            });
        }

        private void openOverlayPermissionSettings() {

            try {

                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );

                startActivity(intent);

            } catch (Exception e) {
                showToast("Overlay Settings नहीं खुलीं");
            }
        }

        @JavascriptInterface
        public void openApp(String packageName) {

            if (packageName == null || packageName.trim().isEmpty()) {
                return;
            }

            runOnUiThread(() -> {

                try {

                    Intent intent = getPackageManager()
                            .getLaunchIntentForPackage(packageName.trim());

                    if (intent != null) {
                        startActivity(intent);
                    } else {
                        showToast("यह App नहीं खुल पाया");
                    }

                } catch (Exception e) {
                    showToast("App खोलने में समस्या हुई");
                }
            });
        }

        @JavascriptInterface
        public void openSystemSettings() {

            runOnUiThread(() -> {

                try {
                    startActivity(
                            new Intent(Settings.ACTION_SETTINGS)
                    );
                } catch (Exception e) {
                    showToast("Settings नहीं खुलीं");
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {

        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
            pendingWebPermission = null;
        }

        if (webView != null) {

            webView.removeJavascriptInterface("Android");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
    }
