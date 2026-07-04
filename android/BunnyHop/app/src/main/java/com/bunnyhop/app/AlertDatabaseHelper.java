package com.bunnyhop.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AlertDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "bunnyhop_alerts.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_ALERTS = "alerts";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_DEVICE_NAME = "device_name";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_ACTIVE = "active";
    private static final String COLUMN_CHANNELS = "channels";

    public AlertDatabaseHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ALERTS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_DEVICE_NAME + " TEXT NOT NULL, "
                + COLUMN_MESSAGE + " TEXT NOT NULL, "
                + COLUMN_ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + COLUMN_CHANNELS + " TEXT DEFAULT 'BLE,NOTIFICATION'"
                + ");");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALERTS);
        onCreate(db);
    }

    public long saveAlert(@NonNull Alert alert) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DEVICE_NAME, alert.getDeviceName());
        values.put(COLUMN_MESSAGE, alert.getMessage());
        values.put(COLUMN_ACTIVE, alert.isActive() ? 1 : 0);
        values.put(COLUMN_CHANNELS, alert.getChannels());

        if (alert.getId() > 0) {
            return db.update(TABLE_ALERTS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(alert.getId())});
        }

        return db.insert(TABLE_ALERTS, null, values);
    }

    @Nullable
    public Alert getAlert(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_ALERTS, null, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @NonNull
    public List<Alert> getAlerts() {
        List<Alert> alerts = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_ALERTS, null, null, null, null, null, COLUMN_DEVICE_NAME + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    alerts.add(fromCursor(cursor));
                }
            }
        }
        return alerts;
    }

    public void deleteAlert(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_ALERTS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    @Nullable
    public String getActiveAlertMessage(@NonNull String deviceName) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_ALERTS, null, COLUMN_DEVICE_NAME + " = ? AND " + COLUMN_ACTIVE + " = 1", new String[]{deviceName}, null, null, null, "1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE));
            }
        }
        return null;
    }

    @NonNull
    private Alert fromCursor(@NonNull Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
        String deviceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NAME));
        String message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE));
        boolean active = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ACTIVE)) == 1;
        String channels = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHANNELS));
        return new Alert(id, deviceName, message, active, channels);
    }
}
