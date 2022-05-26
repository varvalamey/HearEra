package com.variksoid.hearera.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

import com.variksoid.hearera.BuildConfig;

/**
 * Audio Contract specifying tables, columns and other constants related to the database
 */

public class HearEraContract {
    // константы
    static final String CONTENT_AUTHORITY = BuildConfig.APPLICATION_ID;
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    static final String PATH_AUDIO_FILES = "audio";
    static final String PATH_AUDIO_FILES_DISTINCT = "audio_distinct";

    static final String PATH_ALBUM = "album";
    static final String PATH_ALBUM_DISTINCT = "album_distinct";

    static final String PATH_BOOKMARK = "bookmark";
    static final String PATH_BOOKMARK_DISTINCT = "bookmark_distinct";

    static final String PATH_DIRECTORY = "directory";
    static final String PATH_DIRECTORY_DISTINCT = "directory_distinct";

    // Класс для таблицы аудиофайлов
    public static abstract class AudioEntry implements BaseColumns {
        // URI содержимого для таблицы аудио
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_AUDIO_FILES);

        // MIME-тип CONTENT_URI для списка аудио.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO_FILES;

        // MIME-тип CONTENT_URI для одного аудио.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO_FILES_DISTINCT;

        public static final String TABLE_NAME = "audio_files";

        // Колонны
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_ALBUM = "album";
        static final String COLUMN_PATH = "path";
        public static final String COLUMN_TIME = "time";
        public static final String COLUMN_COMPLETED_TIME = "completed_time";
    }

    // Класс для таблицы альбомов
    public static abstract class AlbumEntry implements BaseColumns {
        // URI содержимого для таблицы альбомов
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALBUM);

        // MIME-тип CONTENT_URI для списка альбомов
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALBUM;

        // MIME-тип CONTENT_URI для одного альбома.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALBUM_DISTINCT;

        static final String TABLE_NAME = "albums";

        // Колонны
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_COVER_PATH = "cover_path";
        public static final String COLUMN_DIRECTORY = "directory";
        public static final String COLUMN_LAST_PLAYED = "last_played";
    }

    // Класс для таблицы закладок
    public static abstract class BookmarkEntry implements BaseColumns {
        // URI содержимого для таблицы закладок
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_BOOKMARK);

        // MIME-тип CONTENT_URI для списка закладок.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOOKMARK;

        // MIME-тип CONTENT_URI для одной закладки.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOOKMARK_DISTINCT;

        static final String TABLE_NAME = "bookmarks";

        // Колонны
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_POSITION = "position";
        public static final String COLUMN_AUDIO_FILE = "audio_file";
    }

    // Класс для таблицы директорий
    public static abstract class DirectoryEntry implements BaseColumns {
        // URI содержимого для таблицы директорий
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_DIRECTORY);

        // MIME-тип CONTENT_URI для списка директорий.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_DIRECTORY;

        // MIME-тип CONTENT_URI для одной директории
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_DIRECTORY_DISTINCT;

        static final String TABLE_NAME = "directories";

        // Колонны
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_PATH = "path";
        public static final String COLUMN_TYPE = "type";
    }
}
