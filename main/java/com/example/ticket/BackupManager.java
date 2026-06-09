package com.example.ticket;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private Context context;
    private Gson gson;

    public BackupManager(Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static class BackupData {
        public long backupTime;
        public String appVersion;
        public List<TicketData> tickets;

        public BackupData() {
            tickets = new ArrayList<>();
        }
    }

    public static class TicketData {
        public String filerName;
        public String subject;
        public String amount;
        public String status;
        public long createdAt;
        public long updatedAt;
    }

    public String createBackupJson() {
        BackupData backupData = new BackupData();
        backupData.backupTime = System.currentTimeMillis();
        backupData.appVersion = "1.0";
        backupData.tickets = getAllTickets();

        return gson.toJson(backupData);
    }

    private List<TicketData> getAllTickets() {
        List<TicketData> tickets = new ArrayList<>();
        DBHelper dbHelper = new DBHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = dbHelper.getAllData(db);

        if (cursor.moveToFirst()) {
            do {
                try {
                    TicketData ticket = new TicketData();
                    ticket.filerName = cursor.getString(cursor.getColumnIndexOrThrow("filerName"));
                    ticket.subject = cursor.getString(cursor.getColumnIndexOrThrow("subject"));
                    ticket.amount = cursor.getString(cursor.getColumnIndexOrThrow("amount"));
                    ticket.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                    // Try to get timestamps if they exist
                    try {
                        ticket.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                    } catch (IllegalArgumentException e) {
                        ticket.createdAt = System.currentTimeMillis();
                    }
                    try {
                        ticket.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
                    } catch (IllegalArgumentException e) {
                        ticket.updatedAt = System.currentTimeMillis();
                    }

                    tickets.add(ticket);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Error reading ticket", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return tickets;
    }

    public RestoreResult restoreFromBackup(String backupJson) {
        RestoreResult result = new RestoreResult();

        try {
            Type type = new TypeToken<BackupData>() {}.getType();
            BackupData backupData = gson.fromJson(backupJson, type);

            if (backupData == null || backupData.tickets == null) {
                result.success = false;
                result.message = "Invalid backup data";
                return result;
            }

            DBHelper dbHelper = new DBHelper(context);
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // Clear existing data
            db.delete("tickets", null, null);

            // Insert restored data
            int restoredCount = 0;
            for (TicketData ticket : backupData.tickets) {
                long row = dbHelper.insertData(db, ticket.filerName, ticket.subject, ticket.amount, ticket.status);
                if (row >= 0) {
                    restoredCount++;
                }
            }

            db.close();

            result.success = true;
            result.message = "Restored " + restoredCount + " tickets from backup";
            result.restoredCount = restoredCount;
            result.backupTime = backupData.backupTime;

        } catch (Exception e) {
            Log.e(TAG, "Restore error", e);
            result.success = false;
            result.message = "Restore failed: " + e.getMessage();
        }

        return result;
    }

    public static class RestoreResult {
        public boolean success;
        public String message;
        public int restoredCount;
        public long backupTime;
    }
}