package com.mahmuduls.messmanager;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Downloads a release APK through DownloadManager and starts the system
 * installer. Same flow as Meter Manager: the download id is persisted so the
 * download-complete receiver and getDownloadProgress() survive a restart, and
 * installApk() is triggered once getDownloadProgress() reports 100%.
 */
public class APKInstaller {
    private static final String TAG = "APKInstaller";
    private static final String APK_NAME = "update.apk";

    private final Context context;
    private long pendingDownloadId = -1;
    private String pendingApkPath;
    // Once the installer has been launched, progress must stay at 100 so the
    // dialog button keeps showing "Installing..." instead of falling back.
    private boolean installStarted = false;

    public APKInstaller(Context context) {
        this.context = context;
        restoreState();
        registerDownloadReceiver();
    }

    private void restoreState() {
        try {
            android.content.SharedPreferences prefs =
                context.getSharedPreferences("MessManager", Context.MODE_PRIVATE);
            pendingDownloadId = prefs.getLong("pendingDownloadId", -1);
            pendingApkPath = prefs.getString("pendingApkPath", null);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore pending download state", e);
        }
    }

    private void saveState() {
        try {
            context.getSharedPreferences("MessManager", Context.MODE_PRIVATE).edit()
                .putLong("pendingDownloadId", pendingDownloadId)
                .putString("pendingApkPath", pendingApkPath)
                .apply();
        } catch (Exception e) {
            Log.w(TAG, "Could not persist pending download state", e);
        }
    }

    /* Keeps the DownloadManager id in sync across process restarts. Install is
       driven by getDownloadProgress() so the button can show Installing... first. */
    private void registerDownloadReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == -1) id = pendingDownloadId;
                if (id > 0 && id == pendingDownloadId) {
                    Log.d(TAG, "Update download complete: " + id);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not register download receiver", e);
        }
    }

    /** Enqueues the APK download; a stale file from an earlier attempt is removed first. */
    public void downloadAndInstall(String url) {
        try {
            if (url == null || url.trim().isEmpty()) return;

            // Remove any APK left over from a previous attempt so DownloadManager
            // does not fail with "destination already in use".
            try {
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null) {
                    File stale = new File(dir, APK_NAME);
                    if (stale.exists() && !stale.delete()) {
                        Log.w(TAG, "Could not delete stale update file " + stale);
                    }
                    pendingApkPath = stale.getAbsolutePath();
                }
            } catch (Exception e) {
                Log.w(TAG, "Stale file cleanup failed", e);
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url.trim()));
            request.setTitle("Mess Manager Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_NAME);

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) return;
            pendingDownloadId = manager.enqueue(request);
            saveState();
        } catch (Exception e) {
            Log.e(TAG, "Update download error", e);
        }
    }

    /** 0-100 download progress; reports 100 and launches the installer when done. */
    public int getDownloadProgress() {
        if (installStarted) return 100;
        if (pendingDownloadId == -1) return 0;
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return 0;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(pendingDownloadId);
        Cursor cursor = manager.query(query);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    installApk();
                    return 100;
                }
                int downIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                long downloaded = downIdx >= 0 ? cursor.getLong(downIdx) : 0;
                long total = totalIdx >= 0 ? cursor.getLong(totalIdx) : 0;
                if (total > 0) return (int) ((downloaded * 100L) / total);
            } finally {
                cursor.close();
            }
        } else if (cursor != null) {
            cursor.close();
        }
        return 0;
    }

    /** Starts the system installer for the downloaded APK via FileProvider. */
    private void installApk() {
        try {
            if (installStarted) return;
            installStarted = true;
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = null;
            if (manager != null && pendingDownloadId != -1) {
                try {
                    uri = manager.getUriForDownloadedFile(pendingDownloadId);
                } catch (Exception e) {
                    uri = null;
                }
            }
            if (uri == null && pendingApkPath != null) {
                File f = new File(pendingApkPath);
                if (f.exists()) {
                    uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        ? FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f)
                        : Uri.fromFile(f);
                }
            }
            if (uri == null) {
                Log.w(TAG, "Downloaded APK not found");
                installStarted = false;
                return;
            }

            // file:// URIs must go through the FileProvider on N+.
            if ("file".equals(uri.getScheme()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                File f = pendingApkPath != null ? new File(pendingApkPath) : (uri.getPath() != null ? new File(uri.getPath()) : null);
                if (f != null && f.exists()) {
                    uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
                }
            }

            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // getDownloadProgress() runs on the WebView's JS thread — launch the
            // installer from the main thread.
            final long id = pendingDownloadId;
            final String path = pendingApkPath;
            pendingDownloadId = -1;
            pendingApkPath = null;
            saveState();

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    context.startActivity(install);
                } catch (Exception e) {
                    Log.e(TAG, "Install start failed", e);
                }
            });

            // The system installer needs a moment to read the file; drop the
            // DownloadManager entry and the cached APK afterwards.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (manager != null && id != -1) manager.remove(id);
                } catch (Exception ignored) {}
                try {
                    if (path != null) {
                        File f = new File(path);
                        if (f.exists()) f.delete();
                    }
                } catch (Exception ignored) {}
            }, 15000);
        } catch (Exception e) {
            Log.e(TAG, "Install start failed", e);
        }
    }
}
