package com.variksoid.hearera.helpers;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.data.HearEraDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

public class Migrator {

    private static final String LOG_TAG = Migrator.class.getName();
    private final Context mContext;

    public Migrator(Context context) {
        mContext = context;
    }
    /*
     * Импорб дб из указанного файла
     */
    public void importDatabase(File dbFile) {
        try {
            File dbFileShm = new File(dbFile + "-shm");
            File dbFileWal = new File(dbFile + "-wal");
            File[] importFiles = {dbFile, dbFileShm, dbFileWal};

            SQLiteDatabase db = mContext.openOrCreateDatabase(HearEraDbHelper.DATABASE_NAME, Context.MODE_PRIVATE, null);
            String newDBPath = db.getPath();
            db.close();

            File newDBFile = new File(newDBPath);
            File newDBShm = new File(newDBPath + "-shm");
            File newDBWal = new File(newDBPath + "-wal");
            File[] newFiles = {newDBFile, newDBShm, newDBWal};

            int fileExists = 0;
            for (int i = 0; i < importFiles.length; i++) {
                if (importFiles[i].exists()) {
                    FileChannel src = new FileInputStream(importFiles[i]).getChannel();
                    FileChannel dst = new FileOutputStream(newFiles[i]).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();

                    fileExists++;
                } else {
                    newFiles[i].delete();
                }
            }
            if (fileExists > 0) {
                // Отрегулируйте пути обложек альбомов, чтобы они содержали только имя файла обложки, чтобы включить
                // импорт баз данных, которые были экспортированы в предыдущей версии, с полными путями
                // Получить старый путь обложки
                String[] proj = new String[]{
                        HearEraContract.AlbumEntry._ID,
                        HearEraContract.AlbumEntry.COLUMN_COVER_PATH};
                Cursor c = mContext.getContentResolver().query(HearEraContract.AlbumEntry.CONTENT_URI,
                        proj, null, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        while (c.moveToNext()) {
                            String oldCoverPath = c.getString(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_COVER_PATH));
                            int id = c.getInt(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry._ID));
                            if (oldCoverPath != null && !oldCoverPath.isEmpty()) {
                                // Replace the old cover path in the database by the new relative path
                                String newCoverPath = new File(oldCoverPath).getName();
                                ContentValues values = new ContentValues();
                                values.put(HearEraContract.AlbumEntry.COLUMN_COVER_PATH, newCoverPath);
                                Uri albumUri = ContentUris.withAppendedId(HearEraContract.AlbumEntry.CONTENT_URI, id);
                                mContext.getContentResolver().update(albumUri, values, null, null);
                            }
                        }
                    }
                    c.close();
                }
                Toast.makeText(mContext.getApplicationContext(), R.string.import_success, Toast.LENGTH_LONG).show();
            }

            // Убедитесь, что onUpgrade() вызывается, если была импортирована база данных версии 1 или 2
            // onUpgrade() вызывается при вызове getReadableDatabase() или getWriteableDatabase()
            HearEraDbHelper dbHelper = new HearEraDbHelper(mContext);
            dbHelper.getWritableDatabase();
        } catch (Exception e) {
            Toast.makeText(mContext.getApplicationContext(), R.string.import_fail, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, e.getMessage());

        }
    }

    /*
     * Экспорт базы данных в указанный каталог
     */
    public void exportDatabase(File directory) {
        try {
            if (directory.canWrite()) {
                String currentDBPath = mContext.openOrCreateDatabase(HearEraDbHelper.DATABASE_NAME, Context.MODE_PRIVATE, null).getPath();

                File currentDB = new File(currentDBPath);
                File currentDBShm = new File(currentDBPath + "-shm");
                File currentDBWal = new File(currentDBPath + "-wal");
                File[] currentFiles = {currentDB, currentDBShm, currentDBWal};

                String backupDBPath = "hearera.db";
                File backupDB = new File(directory, backupDBPath);
                File backupDBShm = new File(directory, backupDBPath + "-shm");
                File backupDBWal = new File(directory, backupDBPath + "-wal");
                File[] backupFiles = {backupDB, backupDBShm, backupDBWal};

                int fileExists = 0;
                for (int i = 0; i < currentFiles.length; i++) {
                    if (currentFiles[i].exists()) {
                        FileChannel src = new FileInputStream(currentFiles[i]).getChannel();
                        FileChannel dst = new FileOutputStream(backupFiles[i]).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();

                        fileExists++;
                    }
                }
                if (fileExists > 0) {
                    String successStr = mContext.getResources().getString(R.string.export_success, backupDB.getAbsoluteFile());
                    Toast.makeText(mContext.getApplicationContext(), successStr, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(mContext.getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(mContext.getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, e.getMessage());
        }
    }
}
