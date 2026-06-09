package com.example.ticket;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SyncService extends Service {

    private static final String TAG = "SyncService";
    private static final String PREFS_NAME = "GoogleDrivePrefs";
    private static final String KEY_AUTO_BACKUP = "auto_backup_enabled";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";

    // Status constants
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_SYNCING = 1;
    public static final int STATUS_SYNCED = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_NO_DATA = 4;

    // Static variables for status tracking
    private static int currentStatus = STATUS_IDLE;
    private static String currentMessage = "";
    private static int currentCount = 0;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private HandlerThread handlerThread;
    private Handler workHandler;
    private boolean isSyncing = false;
    private GoogleDriveHelper driveHelper;

    @Override
    public void onCreate() {
        super.onCreate();

        handlerThread = new HandlerThread("SyncWorkThread");
        handlerThread.start();
        workHandler = new Handler(handlerThread.getLooper());

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize Google Drive Helper
        driveHelper = new GoogleDriveHelper(this, new GoogleDriveHelper.BackupListener() {
            @Override
            public void onBackupSuccess(String message) {
                updateStatus(STATUS_SYNCED, message, 0);
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis()).apply();
                isSyncing = false;
            }

            @Override
            public void onBackupFailed(String error) {
                updateStatus(STATUS_FAILED, error, 0);
                isSyncing = false;
            }

            @Override
            public void onRestoreSuccess(int recordCount) {
                updateStatus(STATUS_SYNCED, "Restored " + recordCount + " records", recordCount);
                isSyncing = false;
            }

            @Override
            public void onRestoreFailed(String error) {
                updateStatus(STATUS_FAILED, error, 0);
                isSyncing = false;
            }

            @Override
            public void onBackupProgress(int current, int total) {
                updateStatus(STATUS_SYNCING, "Backing up... " + current + "/" + total, current);
            }

            @Override
            public void onSignInRequired() {
                updateStatus(STATUS_FAILED, "Please sign in to Google Drive first", 0);
                isSyncing = false;
            }

            @Override
            public void onDriveServiceReady() {
                Log.d(TAG, "Drive service is ready for operations");
                updateStatus(STATUS_IDLE, "Drive service ready", 0);
            }
        });

        // Register network callback for automatic sync
        registerNetworkCallback();

        // Start periodic backup check
        startPeriodicBackupCheck();

        Log.d(TAG, "SyncService created");
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "Internet connected! Checking for auto-backup...");
                    workHandler.postDelayed(() -> checkAndBackup(), 5000);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    Log.d(TAG, "Internet lost");
                    updateStatus(STATUS_FAILED, "Internet disconnected", 0);
                }
            };

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }

    private void startPeriodicBackupCheck() {
        // Check for backup every 6 hours
        workHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndBackup();
                workHandler.postDelayed(this, 6 * 60 * 60 * 1000); // 6 hours
            }
        }, 30 * 60 * 1000); // First check after 30 minutes
    }

    private void checkAndBackup() {
        if (isSyncing) {
            Log.d(TAG, "Already syncing, skipping...");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP, true);

        if (!autoBackupEnabled) {
            Log.d(TAG, "Auto-backup disabled");
            return;
        }

        if (!driveHelper.isSignedIn()) {
            Log.d(TAG, "Not signed in to Google Drive");
            return;
        }

        if (!isNetworkAvailable()) {
            Log.d(TAG, "No internet connection");
            return;
        }

        // Check if there's new data since last backup
        long lastBackupTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0);
        if (hasNewDataSince(lastBackupTime)) {
            performBackup();
        } else {
            Log.d(TAG, "No new data to backup");
            updateStatus(STATUS_NO_DATA, "No new data to backup", 0);
        }
    }

    private boolean hasNewDataSince(long timestamp) {
        if (timestamp == 0) return true;

        DBHelper dbHelper = new DBHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try {
            // Check if there are any tickets
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DBHelper.TABLE_TICKETS, null);
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
            return count > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking new data", e);
            return true;
        } finally {
            db.close();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            @SuppressWarnings("deprecation")
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    private void updateStatus(int status, String message, int count) {
        currentStatus = status;
        currentMessage = message;
        currentCount = count;
        Log.d(TAG, "Status update: " + status + " - " + message);
    }

    // Public static methods to get sync status
    public static int getSyncStatus() {
        return currentStatus;
    }

    public static String getSyncMessage() {
        return currentMessage;
    }

    public static int getSyncedCount() {
        return currentCount;
    }

    private void performBackup() {
        if (isSyncing) return;
        isSyncing = true;

        updateStatus(STATUS_SYNCING, "Starting backup to Google Drive...", 0);

        // Use the Google Drive Helper to perform backup
        driveHelper.performBackup();
    }

    public static void startSyncService(Context context) {
        Intent intent = new Intent(context, SyncService.class);
        context.startService(intent);
    }

    public static void stopSyncService(Context context) {
        Intent intent = new Intent(context, SyncService.class);
        context.stopService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "MANUAL_SYNC".equals(intent.getAction())) {
            if (driveHelper.isSignedIn() && isNetworkAvailable()) {
                performBackup();
            } else if (!driveHelper.isSignedIn()) {
                updateStatus(STATUS_FAILED, "Not signed in to Google Drive. Please sign in first.", 0);
            } else if (!isNetworkAvailable()) {
                updateStatus(STATUS_FAILED, "No internet connection. Will backup when connected.", 0);
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        Log.d(TAG, "SyncService destroyed");
    }

    private void showBackupNotification(String filePath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "backup_channel",
                    "Backup Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }

            Notification notification = new Notification.Builder(this, "backup_channel")
                    .setContentTitle("Backup Complete")
                    .setContentText("Your tickets have been backed up to Google Drive")
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setAutoCancel(true)
                    .build();

            if (notificationManager != null) {
                notificationManager.notify(1, notification);
            }
        }
    }
}