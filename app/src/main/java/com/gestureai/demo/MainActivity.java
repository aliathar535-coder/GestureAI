
package com.gestureai.demo;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.net.Uri;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.Toast;

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

    private static final int PERMISSION_CODE = 7;

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
                    boolean camera =
                            ContextCompat.checkSelfPermission(
                                    MainActivity.this,
                                    Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED;

                    boolean audio =
                            ContextCompat.checkSelfPermission(
                                    MainActivity.this,
                                    Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED;

                    if (camera && audio) {
                        request.grant(request.getResources());
                    } else if (camera) {
                        for (String resource : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                                request.grant(new String[]{resource});
                                return;
                            }
                        }
                        request.deny();
                    } else {
                        request.deny();
                        requestPermissionsNow();
                    }
                });
            }
        });

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        requestPermissionsNow();
    }

    private void requestPermissionsNow() {
        boolean camera =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;

        boolean audio =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;

        if (!camera || !audio) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                    },
                    PERMISSION_CODE
            );
        }
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

                    String label = info.loadLabel(getPackageManager()).toString();
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
                    if (packageName == null || packageName.trim().isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                "App select nahi hua",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent launchIntent =
                            getPackageManager().getLaunchIntentForPackage(packageName);

                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Is app ko open nahi kar sakte",
                                Toast.LENGTH_SHORT).show();
                    }

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "App open nahi hua",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void requestPermissions() {
            runOnUiThread(() -> requestPermissionsNow());
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
