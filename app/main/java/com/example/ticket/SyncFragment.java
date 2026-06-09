package com.example.ticket;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SyncFragment extends Fragment implements GoogleDriveHelper.BackupListener, GoogleDriveHelper.BackupListListener {

    private static final String TAG = "SyncFragment";

    // UI Components
    private TextView tvConnectionStatus;
    private TextView tvSyncStatus;
    private TextView tvLastBackupTime;
    private TextView tvAutoBackupStatus;
    private TextView tvDebugInfo;
    private ProgressBar progressBar;
    private Button btnSignIn;
    private Button btnBackupNow;
    private Button btnRestore;
    private Button btnViewBackups;
    private Button btnDebug;
    private SwitchCompat swAutoBackup;
    private RecyclerView recyclerViewBackups;

    // Preferences
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "GoogleDrivePrefs";
    private static final String KEY_AUTO_BACKUP = "auto_backup_enabled";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";

    private GoogleDriveHelper driveHelper;
    private ActivityResultLauncher<Intent> signInLauncher;
    private StringBuilder debugLog = new StringBuilder();
    private boolean isFragmentAttached = false;

    public SyncFragment() {}

    public static SyncFragment newInstance() {
        return new SyncFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        isFragmentAttached = true;
        addDebugLog("Fragment attached to context");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isFragmentAttached = false;
        addDebugLog("Fragment detached from context");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        driveHelper = new GoogleDriveHelper(requireContext(), this);

        addDebugLog("SyncFragment created");

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isFragmentAttached) {
                        addDebugLog("Fragment not attached, ignoring sign-in result");
                        return;
                    }

                    addDebugLog("Sign-in result received. Result code: " + result.getResultCode());
                    addDebugLog("Result data: " + (result.getData() != null ? "present" : "null"));

                    GoogleSignInAccount existingAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
                    addDebugLog("Existing signed-in account: " + (existingAccount != null ? existingAccount.getEmail() : "null"));

                    if (existingAccount != null) {
                        Set<Scope> grantedScopes = existingAccount.getGrantedScopes();
                        addDebugLog("Granted scopes count: " + grantedScopes.size());
                        for (Scope scope : grantedScopes) {
                            addDebugLog("  Scope: " + scope.toString());
                        }

                        boolean hasDriveScope = grantedScopes.contains(new Scope(DriveScopes.DRIVE_FILE));
                        addDebugLog("Has DRIVE_FILE scope: " + hasDriveScope);
                    }

                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        addDebugLog("Processing successful sign-in result...");
                        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                        driveHelper.handleSignInResult(result.getData(), getActivity());
                    } else if (result.getResultCode() == AppCompatActivity.RESULT_CANCELED) {
                        addDebugLog("Sign-in canceled by user or system");

                        if (driveHelper.isSignedIn()) {
                            addDebugLog("Actually signed in despite cancel result!");
                            updateSignInStatus();
                            if (isFragmentAttached) {
                                Toast.makeText(getContext(), "Signed in successfully!", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            String errorDetail = getDetailedCancelReason();
                            addDebugLog("Cancel reason: " + errorDetail);
                            if (isFragmentAttached) {
                                Toast.makeText(getContext(), "Sign in was canceled: " + errorDetail, Toast.LENGTH_LONG).show();
                            }
                        }
                    } else {
                        addDebugLog("Unknown result code: " + result.getResultCode());
                        if (isFragmentAttached) {
                            Toast.makeText(getContext(), "Sign in failed with code: " + result.getResultCode(), Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
    }

    private String getDetailedCancelReason() {
        StringBuilder reasons = new StringBuilder();

        try {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account == null) {
                reasons.append("No account found. ");
            }

            if (account != null) {
                Set<Scope> grantedScopes = account.getGrantedScopes();
                Scope requiredScope = new Scope(DriveScopes.DRIVE_FILE);
                if (!grantedScopes.contains(requiredScope)) {
                    reasons.append("Missing DRIVE_FILE scope. ");
                }
            }

            if (reasons.length() == 0) {
                reasons.append("Unknown cancellation. Check SHA-1 fingerprint in Google Cloud Console.");
            }
        } catch (Exception e) {
            reasons.append("Error checking status: ").append(e.getMessage());
        }

        return reasons.toString();
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
        if (lines.length > 20) {
            StringBuilder newLog = new StringBuilder();
            for (int i = lines.length - 20; i < lines.length; i++) {
                if (newLog.length() > 0) newLog.append("\n");
                newLog.append(lines[i]);
            }
            debugLog = newLog;
        }

        if (tvDebugInfo != null && isFragmentAttached) {
            tvDebugInfo.setText(debugLog.toString());
        }
    }

    private void safeRunOnUiThread(Runnable runnable) {
        if (isFragmentAttached && getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sync, container, false);

        initializeViews(view);
        setupClickListeners();
        loadSettings();
        updateLastBackupDisplay();
        updateSignInStatus();
        checkInitialSignInStatus();

        return view;
    }

    private void checkInitialSignInStatus() {
        addDebugLog("Checking initial sign-in status...");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account != null) {
            addDebugLog("Already signed in as: " + account.getEmail());
            addDebugLog("Account ID: " + account.getId());
            addDebugLog("Account Scopes: " + account.getGrantedScopes().size());
            driveHelper.reinitializeWithAccount(account);
        } else {
            addDebugLog("Not signed in initially");
        }
    }

    private void initializeViews(View view) {
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus);
        tvLastBackupTime = view.findViewById(R.id.tvLastSyncTime);
        tvAutoBackupStatus = view.findViewById(R.id.tvAutoSyncStatus);
        progressBar = view.findViewById(R.id.progressBar);
        btnSignIn = view.findViewById(R.id.btnSetEmail);
        btnBackupNow = view.findViewById(R.id.btnManualSync);
        btnRestore = view.findViewById(R.id.btnRestore);
        btnViewBackups = view.findViewById(R.id.btnViewBackups);
        swAutoBackup = view.findViewById(R.id.swAutoSync);
        recyclerViewBackups = view.findViewById(R.id.recyclerViewBackups);

        tvDebugInfo = view.findViewById(R.id.tvDebugInfo);
        btnDebug = view.findViewById(R.id.btnDebug);

        if (tvDebugInfo == null) {
            addDebugViewsToLayout(view);
        }

        btnSignIn.setText("Sign in with Google");
        btnBackupNow.setText("Backup to Google Drive");

        if (recyclerViewBackups != null) {
            recyclerViewBackups.setLayoutManager(new LinearLayoutManager(getContext()));
        }
    }

    private void addDebugViewsToLayout(View view) {
        try {
            LinearLayout parentLayout = (LinearLayout) view;
            Button debugBtn = new Button(getContext());
            debugBtn.setText("Show Debug Info");
            debugBtn.setOnClickListener(v -> showDebugDialog());
            parentLayout.addView(debugBtn);

            TextView debugText = new TextView(getContext());
            debugText.setPadding(16, 16, 16, 16);
            debugText.setTextSize(10);
            debugText.setBackgroundColor(0x33000000);
            debugText.setVisibility(View.GONE);
            parentLayout.addView(debugText);

            tvDebugInfo = debugText;
            btnDebug = debugBtn;
        } catch (Exception e) {
            Log.e(TAG, "Could not add debug views", e);
        }
    }

    private void setupClickListeners() {
        btnSignIn.setOnClickListener(v -> {
            addDebugLog("Sign-in button clicked");
            if (driveHelper.isSignedIn()) {
                addDebugLog("Already signed in, showing sign out dialog");
                showSignOutDialog();
            } else {
                addDebugLog("Starting sign-in intent");
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                signInLauncher.launch(driveHelper.getSignInIntent());
            }
        });

        btnBackupNow.setOnClickListener(v -> {
            addDebugLog("Backup button clicked");
            performBackup();
        });

        btnRestore.setOnClickListener(v -> {
            addDebugLog("Restore button clicked");
            showRestoreDialog();
        });

        btnViewBackups.setOnClickListener(v -> {
            addDebugLog("View backups button clicked");
            listBackupFiles();
        });

        if (btnDebug != null) {
            btnDebug.setOnClickListener(v -> showDebugDialog());
        }

        swAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_BACKUP, isChecked).apply();
            updateAutoBackupStatus(isChecked);
            addDebugLog("Auto-backup " + (isChecked ? "enabled" : "disabled"));
            if (isFragmentAttached) {
                Toast.makeText(getContext(),
                        isChecked ? "Auto-backup enabled" : "Auto-backup disabled",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDebugDialog() {
        if (!isFragmentAttached) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Debug Information");

        StringBuilder info = new StringBuilder();
        info.append("=== SIGN-IN DEBUG INFO ===\n\n");

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        info.append("Signed in: ").append(account != null ? "YES" : "NO").append("\n");

        if (account != null) {
            info.append("Email: ").append(account.getEmail()).append("\n");
            info.append("Account ID: ").append(account.getId()).append("\n");
            info.append("Display Name: ").append(account.getDisplayName()).append("\n");
            info.append("Scopes granted: ").append(account.getGrantedScopes().size()).append("\n");
            for (Scope scope : account.getGrantedScopes()) {
                info.append("  - ").append(scope.toString()).append("\n");
            }
        }

        info.append("\n=== NETWORK INFO ===\n");
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            info.append("Network connected: ").append(activeNetwork != null && activeNetwork.isConnected()).append("\n");
            if (activeNetwork != null) {
                info.append("Network type: ").append(activeNetwork.getTypeName()).append("\n");
            }
        }

        info.append("\n=== APP INFO ===\n");
        info.append("Package: ").append(requireContext().getPackageName()).append("\n");

        info.append("\n=== DEBUG LOG ===\n");
        info.append(debugLog.toString());

        builder.setMessage(info.toString());
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Copy to Clipboard", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Debug Info", info.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Debug info copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    private void updateSignInStatus() {
        if (!isFragmentAttached) return;

        boolean isSignedIn = driveHelper.isSignedIn();
        addDebugLog("Updating sign-in status: " + (isSignedIn ? "signed in" : "not signed in"));

        if (isSignedIn) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            String email = account != null ? account.getEmail() : "Unknown";
            btnSignIn.setText("Signed In: " + email + " ✓");
            btnSignIn.setEnabled(true);

            if (driveHelper.isDriveServiceReady()) {
                btnBackupNow.setEnabled(true);
                btnRestore.setEnabled(true);
                btnViewBackups.setEnabled(true);
                tvConnectionStatus.setText("✓ Connected to Google Drive as " + email);
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                driveHelper.checkForExistingBackups(new GoogleDriveHelper.BackupCheckListener() {
                    @Override
                    public void onBackupCheck(boolean hasBackups, int count, String error) {
                        if (!isFragmentAttached) return;
                        safeRunOnUiThread(() -> {
                            if (hasBackups) {
                                tvSyncStatus.setText("✓ Found " + count + " backup(s) available - Ready to restore!");
                            } else {
                                tvSyncStatus.setText("No backups found. Tap 'Backup' to create one.");
                            }
                        });
                    }
                });
            } else {
                btnBackupNow.setEnabled(false);
                btnRestore.setEnabled(false);
                btnViewBackups.setEnabled(false);
                tvConnectionStatus.setText("⚠ Signed in, initializing Drive service...");
            }
        } else {
            btnSignIn.setText("Sign in with Google");
            btnBackupNow.setEnabled(false);
            btnRestore.setEnabled(false);
            btnViewBackups.setEnabled(false);
            tvConnectionStatus.setText("✗ Not signed in to Google Drive");
            tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            tvSyncStatus.setText("Sign in with Google to backup/restore your data");
        }
    }

    private void showSignOutDialog() {
        if (!isFragmentAttached) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out from Google Drive?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    addDebugLog("Signing out");
                    driveHelper.signOut();
                    updateSignInStatus();
                    Toast.makeText(getContext(), "Signed out", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadSettings() {
        boolean autoBackup = prefs.getBoolean(KEY_AUTO_BACKUP, true);
        swAutoBackup.setChecked(autoBackup);
        updateAutoBackupStatus(autoBackup);
        addDebugLog("Loaded settings - Auto backup: " + autoBackup);
    }

    private void updateAutoBackupStatus(boolean enabled) {
        if (!isFragmentAttached) return;

        if (enabled) {
            tvAutoBackupStatus.setText("✓ Auto-backup is ON - Will backup automatically when connected");
            tvAutoBackupStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvAutoBackupStatus.setText("○ Auto-backup is OFF - Manual backup only");
            tvAutoBackupStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void updateLastBackupDisplay() {
        if (!isFragmentAttached) return;

        long lastBackup = prefs.getLong(KEY_LAST_BACKUP_TIME, 0);
        if (lastBackup == 0) {
            tvLastBackupTime.setText("Never backed up");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            tvLastBackupTime.setText("Last backup: " + sdf.format(new Date(lastBackup)));
        }
    }

    private void performBackup() {
        if (!isFragmentAttached) return;

        if (!driveHelper.isSignedIn()) {
            addDebugLog("Backup failed: Not signed in");
            Toast.makeText(getContext(), "Please sign in with Google first", Toast.LENGTH_SHORT).show();
            signInLauncher.launch(driveHelper.getSignInIntent());
            return;
        }

        addDebugLog("Starting backup process");
        progressBar.setVisibility(View.VISIBLE);
        btnBackupNow.setEnabled(false);
        tvSyncStatus.setText("Starting backup...");

        driveHelper.performBackup();
    }

    private void showRestoreDialog() {
        if (!isFragmentAttached) return;

        addDebugLog("showRestoreDialog called");

        if (!driveHelper.isSignedIn()) {
            addDebugLog("Restore failed: Not signed in to Google");
            Toast.makeText(getContext(), "Please sign in with Google first", Toast.LENGTH_LONG).show();
            signInLauncher.launch(driveHelper.getSignInIntent());
            return;
        }

        if (!driveHelper.isDriveServiceReady()) {
            addDebugLog("Restore failed: Drive service not ready");
            Toast.makeText(getContext(), "Drive service not ready. Please sign out and sign in again.", Toast.LENGTH_LONG).show();

            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null) {
                addDebugLog("Attempting to reinitialize with existing account: " + account.getEmail());
                driveHelper.initializeAfterSignIn(account);
                new android.os.Handler().postDelayed(() -> {
                    if (isFragmentAttached && driveHelper.isDriveServiceReady()) {
                        showRestoreDialogWithHelper();
                    } else if (isFragmentAttached) {
                        Toast.makeText(getContext(), "Drive service still not ready. Please restart app.", Toast.LENGTH_LONG).show();
                    }
                }, 2000);
            } else {
                signInLauncher.launch(driveHelper.getSignInIntent());
            }
            return;
        }

        showRestoreDialogWithHelper();
    }

    private void showRestoreDialogWithHelper() {
        if (!isFragmentAttached) return;

        addDebugLog("Showing restore dialog - service is ready");
        RestoreBackupDialog dialog = RestoreBackupDialog.newInstance(driveHelper);
        dialog.show(getChildFragmentManager(), "restore_dialog");
    }

    private void listBackupFiles() {
        if (!isFragmentAttached) return;

        if (!driveHelper.isSignedIn()) {
            addDebugLog("List backups failed: Not signed in");
            Toast.makeText(getContext(), "Please sign in with Google first", Toast.LENGTH_SHORT).show();
            return;
        }

        addDebugLog("Listing backup files");
        progressBar.setVisibility(View.VISIBLE);
        btnViewBackups.setEnabled(false);
        tvSyncStatus.setText("Loading backup list...");

        driveHelper.listBackupFiles(this);
    }

    // GoogleDriveHelper.BackupListener implementation
    @Override
    public void onBackupSuccess(String message) {
        if (!isFragmentAttached) return;

        addDebugLog("Backup success: " + message);
        safeRunOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnBackupNow.setEnabled(true);
            tvSyncStatus.setText("✓ " + message);
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            updateLastBackupDisplay();
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackupFailed(String error) {
        if (!isFragmentAttached) return;

        addDebugLog("Backup failed: " + error);
        safeRunOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnBackupNow.setEnabled(true);
            tvSyncStatus.setText("✗ " + error);
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            Toast.makeText(getContext(), "Backup failed: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onRestoreSuccess(int recordCount) {
        if (!isFragmentAttached) return;

        addDebugLog("Restore success: " + recordCount + " records");
        safeRunOnUiThread(() -> {
            Toast.makeText(getContext(), "Restored " + recordCount + " records successfully!", Toast.LENGTH_LONG).show();
            tvSyncStatus.setText("✓ Restored " + recordCount + " records");
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        });
    }

    @Override
    public void onRestoreFailed(String error) {
        if (!isFragmentAttached) return;

        addDebugLog("Restore failed: " + error);
        safeRunOnUiThread(() -> {
            Toast.makeText(getContext(), "Restore failed: " + error, Toast.LENGTH_SHORT).show();
            tvSyncStatus.setText("✗ " + error);
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        });
    }

    @Override
    public void onBackupProgress(int current, int total) {
        if (!isFragmentAttached) return;

        safeRunOnUiThread(() -> {
            if (total > 0) {
                tvSyncStatus.setText("Backing up... " + current + "/" + total);
            } else {
                tvSyncStatus.setText("Preparing backup...");
            }
        });
    }

    @Override
    public void onSignInRequired() {
        if (!isFragmentAttached) return;

        addDebugLog("Sign-in required callback triggered");
        Toast.makeText(getContext(), "Please sign in to continue", Toast.LENGTH_SHORT).show();
        signInLauncher.launch(driveHelper.getSignInIntent());
    }

    @Override
    public void onDriveServiceReady() {
        if (!isFragmentAttached) return;

        addDebugLog("Drive service is ready!");
        safeRunOnUiThread(() -> {
            updateSignInStatus();
            btnBackupNow.setEnabled(true);
            btnRestore.setEnabled(true);
            btnViewBackups.setEnabled(true);
        });
    }

    // GoogleDriveHelper.BackupListListener implementation
    @Override
    public void onSuccess(List<GoogleDriveHelper.BackupFileInfo> backups) {
        if (!isFragmentAttached) return;

        addDebugLog("Backup list success: " + backups.size() + " files found");
        safeRunOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnViewBackups.setEnabled(true);

            if (backups.isEmpty()) {
                tvSyncStatus.setText("No backup files found");
                if (recyclerViewBackups != null) {
                    recyclerViewBackups.setVisibility(View.GONE);
                }
            } else {
                tvSyncStatus.setText("Found " + backups.size() + " backup files");
                if (recyclerViewBackups != null) {
                    recyclerViewBackups.setVisibility(View.VISIBLE);
                    showBackupListDialog(backups);
                }
            }
        });
    }

    private void showBackupListDialog(List<GoogleDriveHelper.BackupFileInfo> backups) {
        if (!isFragmentAttached) return;

        String[] backupNames = new String[backups.size()];
        for (int i = 0; i < backups.size(); i++) {
            backupNames[i] = backups.get(i).name + "\n" + backups.get(i).getFormattedDate();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Available Backups")
                .setItems(backupNames, (dialog, which) -> {
                    GoogleDriveHelper.BackupFileInfo selected = backups.get(which);
                    addDebugLog("Selected backup: " + selected.name);
                    confirmRestoreFromBackup(selected);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestoreFromBackup(GoogleDriveHelper.BackupFileInfo backup) {
        if (!isFragmentAttached) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Restore Backup")
                .setMessage("Restore from:\n" + backup.name + "\n" + backup.getFormattedDate() +
                        "\n\nThis will add new records without deleting existing ones.")
                .setPositiveButton("Restore", (dialog, which) -> {
                    addDebugLog("Confirmed restore from: " + backup.name);
                    performRestore(backup);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performRestore(GoogleDriveHelper.BackupFileInfo backup) {
        if (!isFragmentAttached) return;

        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Restoring...")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        driveHelper.restoreSpecificBackup(backup.id, new GoogleDriveHelper.RestoreListener() {
            @Override
            public void onRestoreComplete(int count) {
                if (!isFragmentAttached) return;
                progressDialog.dismiss();
                addDebugLog("Restore completed: " + count + " records");
                safeRunOnUiThread(() -> {
                    Toast.makeText(getContext(), "Restored " + count + " records!", Toast.LENGTH_LONG).show();
                    onRestoreSuccess(count);
                });
            }

            @Override
            public void onError(String error) {
                if (!isFragmentAttached) return;
                progressDialog.dismiss();
                addDebugLog("Restore error: " + error);
                onRestoreFailed(error);
            }
        });
    }

    @Override
    public void onError(String error) {
        if (!isFragmentAttached) return;

        addDebugLog("Error: " + error);
        safeRunOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnViewBackups.setEnabled(true);
            tvSyncStatus.setText("✗ " + error);
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }
}