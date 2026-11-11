package com.mahmuduls.messmanager;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.getcapacitor.BridgeActivity;
import com.capacitorjs.plugins.statusbar.StatusBarPlugin;
import ee.forgr.capacitor_navigation_bar.CapgoNavigationBarPlugin;
import com.codetrixstudio.capacitor.GoogleAuth.GoogleAuth;

public class MainActivity extends BridgeActivity implements LifecycleEventObserver {
    private Handler mainHandler;
    private APKInstaller apkInstaller;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mainHandler = new Handler(Looper.getMainLooper());
        registerPlugin(StatusBarPlugin.class);
        registerPlugin(CapgoNavigationBarPlugin.class);
        registerPlugin(SystemBarsPlugin.class);
        registerPlugin(GoogleAuth.class);

        // Transparent system bars: the page draws behind them and shows through,
        // so the bars always match the current app background (App Store style).
        applyEdgeToEdge();

        super.onCreate(savedInstanceState);

        applyEdgeToEdge();
        applySavedBarAppearance();

        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        // Forced-update flow: DownloadManager + FileProvider install, driven by
        // AndroidBridge.downloadAndInstall / getDownloadProgress from JS.
        apkInstaller = new APKInstaller(this);

        getLifecycle().addObserver(this);

        // Capacitor's SystemBars/StatusBar plugins apply their own decor background
        // and icon style on the main-thread queue right after bridge load; re-assert
        // ours after they have run.
        mainHandler.postDelayed(() -> {
            applyEdgeToEdge();
            applySavedBarAppearance();
        }, 300);
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            applyEdgeToEdge();
            applySavedBarAppearance();
            pushDarkModeToWebView();
        }
    }

    private void applyEdgeToEdge() {
        try {
            Window window = getWindow();
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            }

            // Window/decor background must match the app theme: with transparent
            // status bar it is what shows behind the bar when the WebView is inset.
            SharedPreferences prefs = getSharedPreferences("MessManager", MODE_PRIVATE);
            String savedTheme = prefs.getString("theme", "system");
            boolean isDark = "system".equals(savedTheme) ? isSystemDarkMode() : "oled".equals(savedTheme);
            getTheme().applyStyle(isDark ? R.style.AppTheme_BarDark : R.style.AppTheme_BarLight, true);
            int color = isDark ? 0xFF0A0A0A : 0xFFF2F4F8;
            window.getDecorView().setBackgroundColor(color);
            window.setBackgroundDrawable(new ColorDrawable(color));
        } catch (Exception e) {}
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyEdgeToEdge();
        applySavedBarAppearance();
        pushDarkModeToWebView();
    }

    private void pushDarkModeToWebView() {
        if (mainHandler == null) return;
        mainHandler.postDelayed(() -> {
            try {
                WebView webView = getBridge().getWebView();
                boolean isDark = isSystemDarkMode();
                webView.evaluateJavascript(
                    "window._androidDarkMode=" + isDark + ";if(typeof App!=='undefined'&&typeof App.applyTheme==='function')App.applyTheme()",
                    null);
            } catch (Exception e) {}
        }, 150);
    }

    private boolean isSystemDarkMode() {
        int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySavedBarAppearance() {
        SharedPreferences prefs = getSharedPreferences("MessManager", MODE_PRIVATE);
        String savedTheme = prefs.getString("theme", "system");
        boolean isDark = "system".equals(savedTheme) ? isSystemDarkMode() : "oled".equals(savedTheme);
        applyBarAppearance(isDark ? "#0a0a0a" : "#f2f4f8");
    }

    private void applyBarAppearance(String colorHex) {
        try {
            Window window = getWindow();
            boolean light = Color.luminance(Color.parseColor(colorHex)) > 0.5;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(light ? mask : 0, mask);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int flags = window.getDecorView().getSystemUiVisibility();
                if (light) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Exception e) {}
    }

    public class WebAppInterface {
        @JavascriptInterface
        public boolean isSystemDarkMode() {
            return MainActivity.this.isSystemDarkMode();
        }

        /** Starts the APK download for a forced update (Meter Manager parity). */
        @JavascriptInterface
        public void downloadAndInstall(String url) {
            if (apkInstaller == null) apkInstaller = new APKInstaller(MainActivity.this);
            apkInstaller.downloadAndInstall(url);
        }

        /** 0-100 download progress; reaches 100 once the installer is launched. */
        @JavascriptInterface
        public int getDownloadProgress() {
            if (apkInstaller == null) apkInstaller = new APKInstaller(MainActivity.this);
            return apkInstaller.getDownloadProgress();
        }

        @JavascriptInterface
        public void setStatusBarColor(String colorHex) {
            mainHandler.post(() -> applyBarAppearance(colorHex));
        }

        @JavascriptInterface
        public String getSystemBarInsets() {
            try {
                WebView webView = getBridge().getWebView();
                View decor = getWindow().getDecorView();
                if (webView == null || decor == null) return "0,0";
                if (webView.getHeight() <= 0 || decor.getHeight() <= 0) return "0,0";
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
                if (insets == null) return "0,0";
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                // Only tell CSS to pad when the WebView actually draws behind the
                // bars. When the system/Capacitor has already inset the content,
                // padding again would double the gap (and raw px are dp here so
                // CSS px match density-independent pixels).
                float density = getResources().getDisplayMetrics().density;
                int[] loc = new int[2];
                webView.getLocationInWindow(loc);
                int top = (loc[1] <= 0) ? Math.round(bars.top / density) : 0;
                boolean atWindowBottom = (loc[1] + webView.getHeight()) >= (decor.getHeight() - 2);
                int bottom = atWindowBottom ? Math.round(bars.bottom / density) : 0;
                return top + "," + bottom;
            } catch (Exception e) {}
            return "0,0";
        }

        @JavascriptInterface
        public void downloadAndInstall(String url) {
            mainHandler.post(() -> {
                new APKInstaller(MainActivity.this).downloadAndInstall(url);
            });
        }

        @JavascriptInterface
        public void saveTheme(String theme) {
            getSharedPreferences("MessManager", MODE_PRIVATE).edit().putString("theme", theme).apply();
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    applyEdgeToEdge();
                    applySavedBarAppearance();
                });
            }
        }
    }
}
