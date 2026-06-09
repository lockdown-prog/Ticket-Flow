package com.example.ticket;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RestoreBackupDialog extends DialogFragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private BackupListAdapter adapter;
    private GoogleDriveHelper driveHelper;
    private RestoreListener restoreListener;
    private List<GoogleDriveHelper.BackupFileInfo> backupFiles = new ArrayList<>();

    public interface RestoreListener {
        void onRestoreCompleted(int count);
    }

    public static RestoreBackupDialog newInstance(GoogleDriveHelper driveHelper) {
        RestoreBackupDialog dialog = new RestoreBackupDialog();
        dialog.setDriveHelper(driveHelper);
        return dialog;
    }

    public void setDriveHelper(GoogleDriveHelper driveHelper) {
        this.driveHelper = driveHelper;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof RestoreListener) {
            restoreListener = (RestoreListener) getParentFragment();
        } else if (context instanceof RestoreListener) {
            restoreListener = (RestoreListener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_restore_backup, null);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BackupListAdapter();
        recyclerView.setAdapter(adapter);

        builder.setView(view)
                .setTitle("Restore from Backup")
                .setPositiveButton("Close", (dialog, which) -> dismiss());

        if (driveHelper == null) {
            Toast.makeText(requireContext(), "Drive service not available", Toast.LENGTH_SHORT).show();
            dismiss();
            return builder.create();
        }

        if (!driveHelper.isDriveServiceReady()) {
            Toast.makeText(requireContext(), "Drive service not ready. Please try again.", Toast.LENGTH_SHORT).show();
            dismiss();
            return builder.create();
        }

        loadBackupList();

        return builder.create();
    }

    private void loadBackupList() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        backupFiles.clear();
        adapter.setBackups(backupFiles);

        driveHelper.listBackupFiles(new GoogleDriveHelper.BackupListListener() {
            @Override
            public void onSuccess(List<GoogleDriveHelper.BackupFileInfo> backups) {
                progressBar.setVisibility(View.GONE);
                backupFiles.clear();
                backupFiles.addAll(backups);

                if (backupFiles.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    tvEmpty.setText("No backup files found.\n\nCreate a backup first by clicking 'Backup to Google Drive'");
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setBackups(backupFiles);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                tvEmpty.setText("Error loading backups:\n" + error);
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    class BackupListAdapter extends RecyclerView.Adapter<BackupListAdapter.ViewHolder> {
        private List<GoogleDriveHelper.BackupFileInfo> backups = new ArrayList<>();

        void setBackups(List<GoogleDriveHelper.BackupFileInfo> backups) {
            this.backups = backups;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_backup_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GoogleDriveHelper.BackupFileInfo backup = backups.get(position);
            holder.tvName.setText(backup.name);
            holder.tvDate.setText(backup.getFormattedDate());

            if (backup.size > 0) {
                holder.tvSize.setText(formatFileSize(backup.size));
                holder.tvSize.setVisibility(View.VISIBLE);
            } else {
                holder.tvSize.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> confirmRestore(backup));
        }

        @Override
        public int getItemCount() {
            return backups.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDate, tvSize;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvDate = itemView.findViewById(R.id.tvFileDate);
                tvSize = itemView.findViewById(R.id.tvFileSize);
            }
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
    }

    private void confirmRestore(GoogleDriveHelper.BackupFileInfo backup) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Restore Backup")
                .setMessage("Are you sure you want to restore from backup?\n\n" +
                        "📁 File: " + backup.name + "\n" +
                        "📅 Date: " + backup.getFormattedDate() + "\n\n" +
                        "⚠️ Note: This will add new records without deleting existing ones.\n" +
                        "Duplicate records (same Filer Name + Subject) will be skipped.")
                .setPositiveButton("Restore", (dialog, which) -> performRestore(backup))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performRestore(GoogleDriveHelper.BackupFileInfo backup) {
        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Restoring...")
                .setMessage("Please wait while restoring from backup:\n\n" + backup.name)
                .setCancelable(false)
                .create();
        progressDialog.show();

        driveHelper.restoreSpecificBackup(backup.id, new GoogleDriveHelper.RestoreListener() {
            @Override
            public void onRestoreComplete(int count) {
                progressDialog.dismiss();
                String message = "Successfully restored " + count + " records!";
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                if (restoreListener != null) {
                    restoreListener.onRestoreCompleted(count);
                }
                dismiss();
            }

            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "Restore failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}