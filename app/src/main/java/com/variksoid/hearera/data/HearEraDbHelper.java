package com.variksoid.hearera.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import com.variksoid.hearera.R;
import com.variksoid.hearera.models.Album;
import com.variksoid.hearera.models.Directory;

import java.util.ArrayList;

/**
 * Audio Database Helper class
 */

public class HearEraDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "HearEra.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 3;

    private static HearEraDbHelper mInstance = null;
    private final Context mContext;

    public HearEraDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create a String that contains the SQL statement to create the audio file table
        String SQL_CREATE_AUDIO_FILE_TABLE = "CREATE TABLE " + HearEraContract.AudioEntry.TABLE_NAME + " ("
                + HearEraContract.AudioEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + HearEraContract.AudioEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + HearEraContract.AudioEntry.COLUMN_ALBUM + " TEXT NOT NULL, "
                + HearEraContract.AudioEntry.COLUMN_PATH + " TEXT, "
                + HearEraContract.AudioEntry.COLUMN_TIME + " INTEGER DEFAULT 0, "
                + HearEraContract.AudioEntry.COLUMN_COMPLETED_TIME + " INTEGER DEFAULT 0);";

        // Create a String that contains the SQL statement to create the album table
        String SQL_CREATE_ALBUM_TABLE = "CREATE TABLE " + HearEraContract.AlbumEntry.TABLE_NAME + " ("
                + HearEraContract.AlbumEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + HearEraContract.AlbumEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + HearEraContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER, "
                + HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER, "
                + HearEraContract.AlbumEntry.COLUMN_COVER_PATH + " TEXT);";

        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE " + HearEraContract.BookmarkEntry.TABLE_NAME + " ("
                + HearEraContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + HearEraContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + HearEraContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";

        // Create a String that contains the SQL statement to create the directory table
        String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + HearEraContract.DirectoryEntry.TABLE_NAME + " ("
                + HearEraContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + HearEraContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                + HearEraContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";

        db.execSQL(SQL_CREATE_AUDIO_FILE_TABLE);
        db.execSQL(SQL_CREATE_ALBUM_TABLE);
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        db.execSQL(SQL_CREATE_DIRECTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        if (i < 2) {
            // Create a String that contains the SQL statement to create the bookmark table
            String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE IF NOT EXISTS " + HearEraContract.BookmarkEntry.TABLE_NAME + " ("
                    + HearEraContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + HearEraContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                    + HearEraContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                    + HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";
            db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        }
        if (i < 3) {
            // Create directory table
            String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + HearEraContract.DirectoryEntry.TABLE_NAME + " ("
                    + HearEraContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + HearEraContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                    + HearEraContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";
            db.execSQL(SQL_CREATE_DIRECTORY_TABLE);

            // Insert current mDirectory from preferences into directory table
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String currentDirPath = prefs.getString(mContext.getString(R.string.preference_filename), "");
            Directory currentDirectory = new Directory(currentDirPath, Directory.Type.PARENT_DIR);
            ContentValues values = currentDirectory.getContentValues();
            // Use direct call to db. Calling Directory.insertIntoDB() yields
            //  java.lang.IllegalStateException: getDatabase called recursively
            long directoryID = db.insert(HearEraContract.DirectoryEntry.TABLE_NAME, null, values);
            currentDirectory.setID(directoryID);

            // Add directory column to album table
            String SQL_ADD_DIRECTORY_COLUMN = "ALTER TABLE " + HearEraContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + HearEraContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER";
            db.execSQL(SQL_ADD_DIRECTORY_COLUMN);

            // Add column_last_played to album table
            String SQL_ADD_LAST_PLAYED_COLUMN = "ALTER TABLE " + HearEraContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER";
            db.execSQL(SQL_ADD_LAST_PLAYED_COLUMN);

            // Populate column_directory in album table
            ArrayList<Album> albums = getAllAlbums(db, currentDirectory);
            for (Album album : albums) {
                ContentValues albumValues = album.getContentValues();
                String selection = HearEraContract.AlbumEntry._ID + "=?";
                String[] selectionArgs = new String[]{String.valueOf(album.getID())};
                db.update(HearEraContract.AlbumEntry.TABLE_NAME, albumValues, selection, selectionArgs);
            }
        }
    }

    static HearEraDbHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new HearEraDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }

    /*
     * Get all albums from the database
     */
    private ArrayList<Album> getAllAlbums(SQLiteDatabase db, Directory directory) {
        ArrayList<Album> albums = new ArrayList<>();
        Cursor c = db.query(HearEraContract.AlbumEntry.TABLE_NAME, Album.getColumns(), null, null, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return albums;
        } else if (c.getCount() < 1) {
            c.close();
            return albums;
        }

        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry._ID));
            String title = c.getString(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_TITLE));
            String coverPath = c.getString(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_COVER_PATH));
            long lastPlayed = -1;
            if (!c.isNull(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED))) {
                lastPlayed = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED));
            }
            Album album = new Album(id, title, directory, coverPath, lastPlayed);
            albums.add(album);
        }
        c.close();

        return albums;
    }
}
