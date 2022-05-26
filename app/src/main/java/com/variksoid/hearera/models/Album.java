package com.variksoid.hearera.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class Album {

    private long mID = -1;
    private final String mTitle;
    private Directory mDirectory;
    private String mCoverPath;
    private long mLastPlayedID;

    private static final String[] mAlbumColumns = new String[]{
            HearEraContract.AlbumEntry._ID,
            HearEraContract.AlbumEntry.COLUMN_TITLE,
            HearEraContract.AlbumEntry.COLUMN_DIRECTORY,
            HearEraContract.AlbumEntry.COLUMN_COVER_PATH,
            HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED
    };

    public Album(long id, String title, Directory directory, String coverPath, long lastPlayed) {
        mID = id;
        mTitle = title;
        mDirectory = directory;
        mCoverPath = coverPath;
        mLastPlayedID = lastPlayed;
    }

    public Album(String title, Directory directory, String coverPath) {
        mTitle = title;
        mDirectory = directory;
        mCoverPath = coverPath;
    }

    public Album(String title, Directory directory) {
        mTitle = title;
        mDirectory = directory;
        updateAlbumCover();
    }

    public long getID() {
        return mID;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getRelativeCoverPath() {
        return mCoverPath;
    }

    public String getCoverPath() {
        if (mCoverPath == null) {
            return null;
        }

        return new File(mDirectory.getPath(), mCoverPath).getAbsolutePath();
    }

    public void setDirectory(Directory directory) {
        mDirectory = directory;
    }

    public String getPath() {
        if (mDirectory == null) {
            return null;
        }

        File albumFile;
        if (mDirectory.getType() == Directory.Type.PARENT_DIR) {
            albumFile = new File(mDirectory.getPath(), mTitle);
        } else {
            albumFile = new File(mDirectory.getPath());
        }
        return albumFile.getAbsolutePath();
    }

    public void setLastPlayedID(long id) {
        mLastPlayedID = id;
    }

    public long getLastPlayedID() {
        return mLastPlayedID;
    }

    static public String[] getColumns() {
        return mAlbumColumns;
    }

    /*
     * Установите обложку альбома, если она еще не установлена или текущая обложка больше не существует
     */
    public String updateAlbumCover() {
        if (mCoverPath == null || !(new File(mDirectory.getPath() + File.separator + mCoverPath).exists())) {
            // Получить каталог альбомов. В зависимости от типа каталога это либо
            // <каталог>/<название альбома> или просто <каталог>.
            File albumDir;
            if (mDirectory.getType() == Directory.Type.PARENT_DIR) {
                albumDir = new File(mDirectory.getPath() + File.separator + mTitle);
            } else {
                albumDir = new File(mDirectory.getPath());
            }
            // Поиск изображений в каталоге альбомов
            mCoverPath = Utils.getImagePath(albumDir);
            // Получить путь обложки относительно каталога альбома
            if (mCoverPath != null) {
                mCoverPath = mCoverPath.replace(mDirectory.getPath(), "");
            }
        }
        return mCoverPath;
    }

    /*
     * Вставить альбом в бд
     */
    public long insertIntoDB(Context context) {
        ContentValues values = getContentValues();
        Uri uri = context.getContentResolver().insert(HearEraContract.AlbumEntry.CONTENT_URI, values);

        if (uri == null) {
            return -1;
        }

        mID = ContentUris.parseId(uri);
        return mID;
    }

    /*
     * Update album in database
     */
    public void updateInDB(Context context) {
        if (mID == -1) {
            return ;
        }
        Uri uri = ContentUris.withAppendedId(HearEraContract.AlbumEntry.CONTENT_URI, mID);
        ContentValues values = getContentValues();
        context.getContentResolver().update(uri, values, null, null);
    }

    /*
     * Put album column values into content values
     */
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(HearEraContract.AlbumEntry.COLUMN_TITLE, mTitle);
        values.put(HearEraContract.AlbumEntry.COLUMN_DIRECTORY, mDirectory.getID());
        values.put(HearEraContract.AlbumEntry.COLUMN_COVER_PATH, mCoverPath);
        values.put(HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED, mLastPlayedID);
        return values;
    }

    /*
     * Retrieve album with given ID from database
     */
    static public Album getAlbumByID(Context context, long id) {
        Uri uri = ContentUris.withAppendedId(HearEraContract.AlbumEntry.CONTENT_URI, id);
        Cursor c = context.getContentResolver().query(uri, mAlbumColumns, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        Album album = null;
        if (c.moveToNext()) {
            album = getAlbumFromPositionedCursor(context, c);
        }
        c.close();

        return album;
    }


    /*
     * Get all albums in the given directory
     */
    public static ArrayList<Album> getAllAlbumsInDirectory(Context context, long directoryId) {
        ArrayList<Album> albums = new ArrayList<>();
        String sel = HearEraContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
        String[] selArgs = {Long.toString(directoryId)};

        Cursor c = context.getContentResolver().query(HearEraContract.AlbumEntry.CONTENT_URI,
                mAlbumColumns, sel, selArgs, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return albums;
        } else if (c.getCount() < 1) {
            c.close();
            return albums;
        }

        while (c.moveToNext()) {
            Album album = getAlbumFromPositionedCursor(context, c);
            albums.add(album);
        }
        c.close();

        return albums;
    }

    private static Album getAlbumFromPositionedCursor(Context context, Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry._ID));
        String title = c.getString(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_TITLE));
        long directoryId = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_DIRECTORY));
        Directory directory = Directory.getDirectoryByID(context, directoryId);
        String coverPath = c.getString(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_COVER_PATH));
        long lastPlayed = -1;
        if (!c.isNull(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED))) {
            lastPlayed = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_LAST_PLAYED));
        }
        return new Album(id, title, directory, coverPath, lastPlayed);
    }
}
