package com.example.ticket;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDriveHelper {
    private static final String TAG = "GoogleDriveHelper";
    private static final int REQUEST_CODE_SIGN_IN = 1001;
    private static final String PREFS_NAME = "GoogleDrivePrefs";
    private static final String KEY_BACKUP_FOLDER_ID = "backup_folder_id";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String BACKUP_FOLDER_NAME = "tickets";  // Changed to "tickets"

    private static final int MAX_BACKUP_FILES = 5;

    private Context context;
    private GoogleSignInClient googleSignInClient;
    private Drive driveService;
    private BackupListener backupListener;
    private ExecutorService executorService;
    private StringBuilder debugLog = new StringBuilder();
    private String cachedFolderId = null;

    public interface BackupListener {
        void onBackupSuccess(String message);
        void onBackupFailed(String error);
        void onRestoreSuccess(int recordCount);
        void onRestoreFailed(String error);
        void onBackupProgress(int current, int total);
        void onSignInRequired();
        void onDriveServiceReady();
    }

    public GoogleDriveHelper(Context context, BackupListener listener) {
        this.context = context;
        this.backupListener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        addDebugLog("GoogleDriveHelper initialized");
        setupGoogleSignIn();

        // Load cached folder ID if exists
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedFolderId = prefs.getString(KEY_BACKUP_FOLDER_ID, null);

        checkAndInitializeExistingSignIn();
    }

    private void checkAndInitializeExistingSignIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account != null) {
            addDebugLog("Found existing signed-in account: " + account.getEmail());
            initializeDriveService(account);
            if (backupListener != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    backupListener.onDriveServiceReady();
                });
            }
        } else {
            addDebugLog("No existing signed-in account found");
        }
    }

    private void addDebugLog(String message) {
        Log.d(TAG, message);
        if (debugLog.length() > 0) {
            debugLog.append("\n");
        }
        debugLog.append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        debugLog.append(": ");
        debugLog.append(message);

        String[] lines = debugLog.toString().split("\n");
        if (lines.length > 30) {
            StringBuilder newLog = new StringBuilder();
            for (int i = lines.length - 30; i < lines.length; i++) {
                if (newLog.length() > 0) newLog.append("\n");
                newLog.append(lines[i]);
            }
            debugLog = newLog;
        }
    }

    private void setupGoogleSignIn() {
        addDebugLog("Setting up Google Sign-In options");

        String webClientId = "676836635057-83ki5jpjosishrh3qro5fbhnjhq3vkrf.apps.googleusercontent.com";

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();

        googleSignInClient = GoogleSignIn.getClient(context, signInOptions);
        addDebugLog("Google Sign-In client created with Web Client ID");
    }

    public Intent getSignInIntent() {
        addDebugLog("Getting sign-in intent");
        return googleSignInClient.getSignInIntent();
    }

    public void handleSignInResult(Intent data, Activity activity) {
        addDebugLog("handleSignInResult called with data: " + (data != null ? "present" : "null"));

        if (data == null) {
            addDebugLog("ERROR: Intent data is null");
            if (backupListener != null) {
                backupListener.onBackupFailed("Sign in failed: No data received");
            }
            return;
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                addDebugLog("Sign-in successful! Account: " + account.getEmail());
                initializeDriveService(account);
                addDebugLog("Drive service initialized");

                if (backupListener != null) {
                    backupListener.onDriveServiceReady();
                    backupListener.onBackupSuccess("Signed in as: " + account.getEmail());
                }
            } else {
                addDebugLog("ERROR: Account is null after sign-in");
                if (backupListener != null) {
                    backupListener.onBackupFailed("Sign in failed: Account is null");
                }
            }
        } catch (ApiException e) {
            int statusCode = e.getStatusCode();
            addDebugLog("Sign-in failed with status code: " + statusCode);
            if (backupListener != null) {
                backupListener.onBackupFailed("Sign in failed: " + e.getMessage());
            }
        }
    }

    public void reinitializeWithAccount(GoogleSignInAccount account) {
        addDebugLog("Reinitializing with account: " + (account != null ? account.getEmail() : "null"));
        if (account != null) {
            initializeDriveService(account);
            if (backupListener != null) {
                backupListener.onDriveServiceReady();
            }
        }
    }

    private void initializeDriveService(GoogleSignInAccount account) {
        addDebugLog("Initializing Drive service for account: " + account.getEmail());

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());

        driveService = new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("TicketFlow")
                .build();

        addDebugLog("Drive service initialized successfully");

        // Create or get the tickets folder
        createOrGetTicketsFolder();
    }

    /**
     * Creates the "tickets" folder in Google Drive or gets its ID if it already exists
     */
    private void createOrGetTicketsFolder() {
        addDebugLog("Creating or getting 'tickets' folder");

        // If we already have the folder ID cached, use it
        if (cachedFolderId != null) {
            addDebugLog("Using cached folder ID: " + cachedFolderId);
            return;
        }

        executorService.execute(() -> {
            try {
                // First, search for existing "tickets" folder
                String query = "name = '" + BACKUP_FOLDER_NAME + "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
                FileList result = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name)")
                        .execute();

                String folderId = null;

                if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                    // Folder already exists
                    folderId = result.getFiles().get(0).getId();
                    addDebugLog("Found existing 'tickets' folder with ID: " + folderId);
                } else {
                    // Create new folder
                    File folderMetadata = new File();
                    folderMetadata.setName(BACKUP_FOLDER_NAME);
                    folderMetadata.setMimeType("application/vnd.google-apps.folder");

                    File folder = driveService.files().create(folderMetadata)
                            .setFields("id")
                            .execute();
                    folderId = folder.getId();
                    addDebugLog("Created new 'tickets' folder with ID: " + folderId);
                }

                // Save folder ID to preferences
                if (folderId != null) {
                    cachedFolderId = folderId;
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(KEY_BACKUP_FOLDER_ID, folderId).apply();
                    addDebugLog("Folder ID saved to preferences");
                }

            } catch (IOException e) {
                addDebugLog("Error creating/getting folder: " + e.getMessage());
                Log.e(TAG, "Folder creation error", e);
            }
        });
    }

    /**
     * Gets the folder ID (creates it if needed)
     */
    private String getOrCreateFolderId() {
        if (cachedFolderId == null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            cachedFolderId = prefs.getString(KEY_BACKUP_FOLDER_ID, null);
        }
        return cachedFolderId;
    }

    public void performBackup() {
        addDebugLog("performBackup called");

        if (driveService == null) {
            addDebugLog("Drive service is null, requiring sign-in");
            if (backupListener != null) {
                backupListener.onSignInRequired();
            }
            return;
        }

        if (!isNetworkAvailable()) {
            addDebugLog("Network not available");
            if (backupListener != null) {
                backupListener.onBackupFailed("No internet connection");
            }
            return;
        }

        executorService.execute(() -> {
            try {
                addDebugLog("Starting backup execution");

                if (backupListener != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> backupListener.onBackupProgress(0, 0));
                }

                // Make sure we have the folder ID
                String folderId = getOrCreateFolderId();
                if (folderId == null) {
                    // Try to create the folder
                    createOrGetTicketsFolder();
                    Thread.sleep(2000); // Wait a bit for folder creation
                    folderId = getOrCreateFolderId();
                    if (folderId == null) {
                        addDebugLog("Failed to get/create folder");
                        if (backupListener != null) {
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> backupListener.onBackupFailed("Could not create backup folder"));
                        }
                        return;
                    }
                }

                // Get all tickets
                ArrayList<TicketModel> tickets = getAllTickets();
                addDebugLog("Found " + tickets.size() + " tickets to backup");

                if (tickets.isEmpty()) {
                    addDebugLog("No tickets to backup");
                    if (backupListener != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> backupListener.onBackupFailed("No tickets to backup. Create some tickets first."));
                    }
                    return;
                }

                // Enforce backup rotation
                enforceBackupRotation(folderId);

                // Generate CSV file
                java.io.File csvFile = generateBackupFile(tickets);
                addDebugLog("Backup file created: " + csvFile.getAbsolutePath());

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "tickets_backup_" + timestamp + ".csv";
                addDebugLog("Uploading file: " + fileName);

                // Upload new file to the tickets folder
                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));

                com.google.api.client.http.FileContent content = new com.google.api.client.http.FileContent(
                        "text/csv", csvFile);

                File uploadedFile = driveService.files().create(fileMetadata, content)
                        .setFields("id, name, createdTime")
                        .execute();

                addDebugLog("File uploaded successfully. File ID: " + uploadedFile.getId());

                // Save backup info
                saveBackupInfo(uploadedFile.getId(), System.currentTimeMillis());

                // Clean up temp file
                if (csvFile.exists()) {
                    csvFile.delete();
                    addDebugLog("Temp file cleaned up");
                }

                if (backupListener != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> backupListener.onBackupSuccess("Backup successful! File: " + fileName + "\nSaved in Drive → tickets folder"));
                }

            } catch (Exception e) {
                addDebugLog("Backup failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
                if (backupListener != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> backupListener.onBackupFailed("Backup failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Enforces the maximum number of backup files (MAX_BACKUP_FILES = 5) in the tickets folder
     */
    private void enforceBackupRotation(String folderId) {
        addDebugLog("Enforcing backup rotation - Max files: " + MAX_BACKUP_FILES);

        try {
            // Get all backup files in the tickets folder
            String query = "'" + folderId + "' in parents and mimeType = 'text/csv' and trashed = false";
            FileList result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime)")
                    .setOrderBy("createdTime asc")
                    .execute();

            List<File> backupFiles = result.getFiles();

            if (backupFiles == null || backupFiles.size() <= MAX_BACKUP_FILES) {
                addDebugLog("Backup count (" + (backupFiles != null ? backupFiles.size() : 0) +
                        ") within limit. No deletion needed.");
                return;
            }

            int filesToDelete = backupFiles.size() - MAX_BACKUP_FILES;
            addDebugLog("Found " + backupFiles.size() + " backup files. Need to delete " + filesToDelete + " oldest file(s).");

            for (int i = 0; i < filesToDelete && i < backupFiles.size(); i++) {
                File oldestFile = backupFiles.get(i);
                addDebugLog("Deleting old backup: " + oldestFile.getName() + " (ID: " + oldestFile.getId() + ")");
                driveService.files().delete(oldestFile.getId()).execute();
                addDebugLog("Successfully deleted: " + oldestFile.getName());
            }

            addDebugLog("Backup rotation completed. " + filesToDelete + " file(s) deleted.");

        } catch (IOException e) {
            addDebugLog("Error during backup rotation: " + e.getMessage());
            Log.e(TAG, "Backup rotation error", e);
        }
    }

    private ArrayList<TicketModel> getAllTickets() {
        ArrayList<TicketModel> tickets = new ArrayList<>();
        DBHelper dbHelper = new DBHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = dbHelper.getAllData(db);
        if (cursor.moveToFirst()) {
            do {
                try {
                    String filerName = cursor.getString(cursor.getColumnIndexOrThrow("filerName"));
                    String subject = cursor.getString(cursor.getColumnIndexOrThrow("subject"));
                    String amount = cursor.getString(cursor.getColumnIndexOrThrow("amount"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    tickets.add(new TicketModel(filerName, subject, amount, status));
                } catch (IllegalArgumentException e) {
                    addDebugLog("Error reading ticket: " + e.getMessage());
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        return tickets;
    }

    private java.io.File generateBackupFile(ArrayList<TicketModel> tickets) throws IOException {
        java.io.File cacheDir = context.getCacheDir();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        java.io.File csvFile = new java.io.File(cacheDir, "tickets_backup_" + timestamp + ".csv");

        try (FileOutputStream fos = new FileOutputStream(csvFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {

            writer.write("Filer Name,Subject,Amount,Status,Backup Time\n");

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String backupTime = dateFormat.format(new Date());

            for (TicketModel ticket : tickets) {
                writer.write(String.format("\"%s\",\"%s\",%s,\"%s\",\"%s\"\n",
                        escapeCsv(ticket.getFilerName()),
                        escapeCsv(ticket.getSubject()),
                        ticket.getPrice(),
                        ticket.getStatus(),
                        backupTime
                ));
            }

            writer.flush();
        }

        return csvFile;
    }

    public void checkForExistingBackups(BackupCheckListener listener) {
        addDebugLog("checkForExistingBackups called");

        if (driveService == null) {
            addDebugLog("Drive service is null");
            if (listener != null) {
                listener.onBackupCheck(false, 0, "Drive service not ready");
            }
            return;
        }

        executorService.execute(() -> {
            try {
                String folderId = getOrCreateFolderId();
                if (folderId == null) {
                    addDebugLog("No folder found");
                    if (listener != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> listener.onBackupCheck(false, 0, null));
                    }
                    return;
                }

                String query = "'" + folderId + "' in parents and mimeType = 'text/csv' and trashed = false";
                FileList result = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name, createdTime)")
                        .setOrderBy("createdTime desc")
                        .execute();

                List<File> backupFiles = result.getFiles();
                boolean hasBackups = backupFiles != null && !backupFiles.isEmpty();
                int count = backupFiles != null ? backupFiles.size() : 0;

                addDebugLog("Backup check: " + count + " backup(s) found");

                final boolean finalHasBackups = hasBackups;
                final int finalCount = count;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onBackupCheck(finalHasBackups, finalCount, null);
                    }
                });

            } catch (IOException e) {
                addDebugLog("Error checking backups: " + e.getMessage());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onBackupCheck(false, 0, e.getMessage());
                    }
                });
            }
        });
    }

    public void listBackupFiles(BackupListListener listener) {
        addDebugLog("listBackupFiles called");

        if (driveService == null) {
            addDebugLog("Drive service is null");
            if (listener != null) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> listener.onError("Not signed in"));
            }
            return;
        }

        executorService.execute(() -> {
            try {
                String folderId = getOrCreateFolderId();
                if (folderId == null) {
                    addDebugLog("No folder found");
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> listener.onSuccess(new ArrayList<>()));
                    return;
                }

                addDebugLog("Querying backup files from folder: " + folderId);

                String query = "'" + folderId + "' in parents and mimeType = 'text/csv' and trashed = false";
                FileList result = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name, createdTime, size)")
                        .setOrderBy("createdTime desc")
                        .execute();

                List<BackupFileInfo> backups = new ArrayList<>();
                if (result.getFiles() != null) {
                    addDebugLog("Found " + result.getFiles().size() + " backup files");
                    for (File file : result.getFiles()) {
                        BackupFileInfo info = new BackupFileInfo();
                        info.id = file.getId();
                        info.name = file.getName();
                        info.createdTime = file.getCreatedTime() != null ?
                                file.getCreatedTime().getValue() : 0;
                        info.size = file.getSize() != null ? file.getSize() : 0;
                        backups.add(info);
                        addDebugLog("  - " + info.name + " (" + info.getFormattedDate() + ") - " + info.size + " bytes");
                    }
                } else {
                    addDebugLog("No backup files found");
                }

                final List<BackupFileInfo> finalBackups = backups;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onSuccess(finalBackups);
                    }
                });

            } catch (IOException e) {
                addDebugLog("Error listing backups: " + e.getMessage());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onError(e.getMessage());
                    }
                });
            }
        });
    }

    public void restoreSpecificBackup(String backupFileId, RestoreListener listener) {
        addDebugLog("restoreSpecificBackup called for ID: " + backupFileId);

        executorService.execute(() -> {
            try {
                addDebugLog("Downloading backup file: " + backupFileId);

                java.io.File tempFile = new java.io.File(context.getCacheDir(), "restore_temp.csv");
                driveService.files().get(backupFileId)
                        .executeMediaAndDownloadTo(new FileOutputStream(tempFile));

                addDebugLog("Download complete, restoring from CSV");

                int restoredCount = restoreFromCsv(tempFile);
                addDebugLog("Restored " + restoredCount + " records");

                if (tempFile.exists()) tempFile.delete();

                final int finalCount = restoredCount;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onRestoreComplete(finalCount);
                    }
                });

            } catch (Exception e) {
                addDebugLog("Restore specific backup failed: " + e.getMessage());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onError(e.getMessage());
                    }
                });
            }
        });
    }

    public void initializeAfterSignIn(GoogleSignInAccount account) {
        addDebugLog("initializeAfterSignIn called for: " + (account != null ? account.getEmail() : "null"));
        if (account != null) {
            initializeDriveService(account);
            addDebugLog("Drive service initialized successfully");
            if (backupListener != null) {
                backupListener.onDriveServiceReady();
            }
        } else {
            addDebugLog("ERROR: Cannot initialize - account is null");
        }
    }

    public boolean isDriveServiceReady() {
        boolean ready = driveService != null;
        addDebugLog("isDriveServiceReady: " + ready);
        return ready;
    }

    private int restoreFromCsv(java.io.File csvFile) throws IOException {
        int restoredCount = 0;
        DBHelper dbHelper = new DBHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        try (FileInputStream fis = new FileInputStream(csvFile);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(fis))) {

            String line;
            boolean isFirstLine = true;

            db.beginTransaction();

            try {
                while ((line = reader.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    if (line.trim().isEmpty()) continue;

                    String[] columns = parseCsvLine(line);
                    if (columns.length >= 4) {
                        String filerName = cleanCsvValue(columns[0]);
                        String subject = cleanCsvValue(columns[1]);
                        String amount = columns[2].trim();
                        String status = cleanCsvValue(columns[3]);

                        String checkQuery = "SELECT * FROM tickets WHERE filerName = ? AND subject = ?";
                        Cursor cursor = db.rawQuery(checkQuery, new String[]{filerName, subject});
                        boolean exists = cursor.getCount() > 0;
                        cursor.close();

                        if (!exists) {
                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put("filerName", filerName);
                            values.put("subject", subject);
                            values.put("amount", amount);
                            values.put("status", status);
                            values.put("sync_status", "PENDING");
                            values.put("created_at", System.currentTimeMillis());
                            values.put("updated_at", System.currentTimeMillis());

                            db.insert("tickets", null, values);
                            restoredCount++;
                        }
                    }
                }

                db.setTransactionSuccessful();
                addDebugLog("Transaction successful, restored " + restoredCount + " records");
            } finally {
                db.endTransaction();
            }

        } finally {
            db.close();
        }

        return restoredCount;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    private String cleanCsvValue(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replaceAll("^\"|\"$", "");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private void saveBackupInfo(String backupId, long backupTime) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("last_backup_id", backupId)
                .putLong(KEY_LAST_BACKUP_TIME, backupTime)
                .apply();
        addDebugLog("Backup info saved - ID: " + backupId + ", Time: " + backupTime);
    }

    public boolean isSignedIn() {
        boolean signedIn = GoogleSignIn.getLastSignedInAccount(context) != null;
        addDebugLog("isSignedIn: " + signedIn);
        return signedIn;
    }

    public void signOut() {
        addDebugLog("Signing out");
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
            driveService = null;
            cachedFolderId = null;
            addDebugLog("Signed out successfully");
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            addDebugLog("ConnectivityManager is null");
            return false;
        }

        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        addDebugLog("Network available: " + isConnected);
        return isConnected;
    }

    public interface BackupListListener {
        void onSuccess(List<BackupFileInfo> backups);
        void onError(String error);
    }

    public interface RestoreListener {
        void onRestoreComplete(int count);
        void onError(String error);
    }

    public interface BackupCheckListener {
        void onBackupCheck(boolean hasBackups, int count, String error);
    }

    public static class BackupFileInfo {
        public String id;
        public String name;
        public long createdTime;
        public long size;

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(createdTime));
        }
    }
}