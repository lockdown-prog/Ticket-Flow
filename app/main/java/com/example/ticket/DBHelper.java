package com.example.ticket;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class DBHelper extends SQLiteOpenHelper {
    public static final String dbName = "TicketFlow.db";
    public static final int version = 1;

    // Table name
    public static final String TABLE_TICKETS = "tickets";

    // Column names
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FILER_NAME = "filerName";
    public static final String COLUMN_SUBJECT = "subject";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_SYNC_STATUS = "sync_status";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_SYNCED_AT = "synced_at";

    public DBHelper(@Nullable Context context) {
        super(context, dbName, null, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_TICKETS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_FILER_NAME + " TEXT NOT NULL," +
                COLUMN_SUBJECT + " TEXT NOT NULL," +
                COLUMN_AMOUNT + " TEXT," +
                COLUMN_STATUS + " TEXT," +
                COLUMN_SYNC_STATUS + " TEXT DEFAULT 'PENDING'," +
                COLUMN_CREATED_AT + " INTEGER DEFAULT (strftime('%s', 'now') * 1000)," +
                COLUMN_UPDATED_AT + " INTEGER DEFAULT (strftime('%s', 'now') * 1000)," +
                COLUMN_SYNCED_AT + " INTEGER DEFAULT 0," +
                "UNIQUE(" + COLUMN_FILER_NAME + ", " + COLUMN_SUBJECT + ")" +
                ")";
        db.execSQL(createTable);

        // Create index for better performance
        String createIndex = "CREATE INDEX idx_sync_status ON " + TABLE_TICKETS + "(" + COLUMN_SYNC_STATUS + ")";
        db.execSQL(createIndex);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop and recreate table on upgrade
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TICKETS);
        onCreate(db);
    }

    // Insert data
    public long insertData(SQLiteDatabase db, String filerName, String subject, String amount, String status) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_FILER_NAME, filerName);
        values.put(COLUMN_SUBJECT, subject);
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_SYNC_STATUS, "PENDING");
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

        return db.insertWithOnConflict(TABLE_TICKETS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // Update data by ID
    public int updateData(SQLiteDatabase db, int id, String subject, String amount, String status) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SUBJECT, subject);
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_SYNC_STATUS, "PENDING");
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

        return db.update(TABLE_TICKETS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    // Delete data by ID
    public int deleteData(SQLiteDatabase db, int id) {
        return db.delete(TABLE_TICKETS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    // Get ticket by ID
    public Cursor getTicketById(SQLiteDatabase db, int id) {
        return db.query(TABLE_TICKETS,
                new String[]{COLUMN_FILER_NAME, COLUMN_SUBJECT, COLUMN_AMOUNT, COLUMN_STATUS, COLUMN_ID},
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null);
    }

    // Get unsynced tickets
    public Cursor getUnsyncedTickets(SQLiteDatabase db) {
        return db.query(TABLE_TICKETS,
                new String[]{COLUMN_FILER_NAME, COLUMN_SUBJECT, COLUMN_AMOUNT, COLUMN_STATUS, COLUMN_ID},
                COLUMN_SYNC_STATUS + " != 'SYNCED' OR " + COLUMN_SYNC_STATUS + " IS NULL",
                null, null, null, null);
    }

    // Mark ticket as synced
    public void markAsSynced(SQLiteDatabase db, int id) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_STATUS, "SYNCED");
        values.put(COLUMN_SYNCED_AT, System.currentTimeMillis());
        db.update(TABLE_TICKETS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }



    public Cursor getAllData(SQLiteDatabase db){
        Cursor cursor = db.query("tickets",null,null,null,null,null,null);
        return cursor;
    }
}
