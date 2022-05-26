package com.variksoid.hearera.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

/**
 * Content Provider for audio_anchor app
 */

public class HearEraProvider extends ContentProvider {


    public static final String LOG_TAG = HearEraProvider.class.getSimpleName();

    // Экземпляр HearEraDbHelper для получения доступа к базе данных аудио
    private HearEraDbHelper mDbHelper;

    private static final int AUDIO = 100;
    private static final int AUDIO_ID = 101;
    private static final int AUDIO_DISTINCT = 110;

    private static final int ALBUM = 200;
    private static final int ALBUM_ID = 201;
    private static final int ALBUM_DISTINCT = 210;

    private static final int BOOKMARK = 300;
    private static final int BOOKMARK_ID = 301;
    private static final int BOOKMARK_DISTINCT = 310;

    private static final int DIRECTORY = 500;
    private static final int DIRECTORY_ID = 501;
    private static final int DIRECTORY_DISTINCT = 510;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // URI для таблицы аудиофайлов
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_AUDIO_FILES, AUDIO);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_AUDIO_FILES + "/#", AUDIO_ID);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_AUDIO_FILES_DISTINCT, AUDIO_DISTINCT);
        // URI для таблицы альбомов
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_ALBUM, ALBUM);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_ALBUM + "/#", ALBUM_ID);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_ALBUM_DISTINCT, ALBUM_DISTINCT);
        // URI для таблицы закладок
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_BOOKMARK, BOOKMARK);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_BOOKMARK + "/#", BOOKMARK_ID);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_BOOKMARK_DISTINCT, BOOKMARK_DISTINCT);
        // URI для таблицы директорий
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_DIRECTORY, DIRECTORY);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_DIRECTORY + "/#", DIRECTORY_ID);
        sUriMatcher.addURI(HearEraContract.CONTENT_AUTHORITY, HearEraContract.PATH_DIRECTORY_DISTINCT, DIRECTORY_DISTINCT);
    }

    /**
     * Инициализируйте поставщика и вспомогательный объект базы данных.
     */
    @Override
    public boolean onCreate() {
        // Create and initialize a HearEraDbHelper object to gain access to the audios database.
        mDbHelper = HearEraDbHelper.getInstance(getContext());
        return true;
    }

    /**
     * Выполнить запрос для данного URI с заданными параметрами.
     */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // Получить читаемую базу данных
        SQLiteDatabase database = mDbHelper.getReadableDatabase();
        SQLiteQueryBuilder qb;
        // Этот курсор будет содержать результат запроса
        Cursor cursor;

        // Выясните, может ли сопоставитель URI сопоставить URI с определенным кодом
        int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Запрос таблицы аудиофайтов с заданными параметрами
                cursor = database.query(HearEraContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case ALBUM:
                // Запрос таблицы альбомов с заданными параметрами
                cursor = database.query(HearEraContract.AlbumEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case BOOKMARK:
                // Запросить таблицу закладок с заданными параметрами
                cursor = database.query(HearEraContract.BookmarkEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case DIRECTORY:
                // Запросить таблицу каталогов с заданными параметрами
                cursor = database.query(HearEraContract.DirectoryEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case AUDIO_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(HearEraContract.AudioEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case ALBUM_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(HearEraContract.AlbumEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case BOOKMARK_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(HearEraContract.BookmarkEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case DIRECTORY_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(HearEraContract.DirectoryEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case AUDIO_ID:
                // Запросить одну строку, заданную ID в URI
                selection = HearEraContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                //Выполнить запрос к таблицам recipe для заданного id.
                cursor = database.query(HearEraContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case ALBUM_ID:
                // Запросить одну строку, заданную ID в URI
                selection = HearEraContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Выполнить запрос к таблицам recipe для заданного id.
                cursor = database.query(HearEraContract.AlbumEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case BOOKMARK_ID:
                // Запросить одну строку, заданную ID в URI
                selection = HearEraContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Выполнить запрос к таблицам recipe для заданного id.
                cursor = database.query(HearEraContract.BookmarkEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case DIRECTORY_ID:
                // Запросить одну строку, заданную ID в URI
                selection = HearEraContract.DirectoryEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Выполнить запрос к таблицам recipe для заданного id.
                cursor = database.query(HearEraContract.DirectoryEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
        }
        // Установите для курсора уведомление Uri, чтобы мы знали, для чего uri был создан курсор.
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    /**
     * Возвращает тип данных MIME для URI контента.
     */
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                return HearEraContract.AudioEntry.CONTENT_LIST_TYPE;
            case ALBUM:
                return HearEraContract.AlbumEntry.CONTENT_LIST_TYPE;
            case BOOKMARK:
                return HearEraContract.BookmarkEntry.CONTENT_LIST_TYPE;
            case DIRECTORY:
                return HearEraContract.DirectoryEntry.CONTENT_LIST_TYPE;
            case AUDIO_ID:
                return HearEraContract.AudioEntry.CONTENT_ITEM_TYPE;
            case ALBUM_ID:
                return HearEraContract.AlbumEntry.CONTENT_ITEM_TYPE;
            case BOOKMARK_ID:
                return HearEraContract.BookmarkEntry.CONTENT_ITEM_TYPE;
            case DIRECTORY_ID:
                return HearEraContract.DirectoryEntry.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }

    /**
     * Вставьте новые данные в провайдер с заданными значениями ContentValues.
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                return insertAudioFile(uri, contentValues);
            case ALBUM:
                return insertAlbum(uri, contentValues);
            case BOOKMARK:
                return insertBookmark(uri, contentValues);
            case DIRECTORY:
                return insertDirectory(uri, contentValues);
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    /**
     * Вставьте в базу данных новый аудиофайл с указанным ContentValues.
     */
    private Uri insertAudioFile(Uri uri, ContentValues values) {
        // проверка значений
        if (!isValidAudioFileEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // COLUMN_PATH не нулевые, а значит и так можно)))))
        values.put(HearEraContract.AudioEntry.COLUMN_PATH, "");

        long id = db.insert(HearEraContract.AudioEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Сообщите всем listeners, что данные по указанному URI изменились
        getContext().getContentResolver().notifyChange(uri, null);

        // Вернуть новый URI с добавленным ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Вставьте в базу данных новый аудиофайл с указанным ContentValues.
     */
    private Uri insertAlbum(Uri uri, ContentValues values) {
        // Проверка данных
        if (!isValidAlbumEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(HearEraContract.AlbumEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Сообщите всем listeners, что данные по указанному URI изменились
        getContext().getContentResolver().notifyChange(uri, null);

        // Вернуть новый URI с добавленным ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Вставьте новую закладку с заданными значениями ContentValues в базу данных.
     */
    private Uri insertBookmark(Uri uri, ContentValues values) {
        // Проверка данных
        if (!isValidBokomarkEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(HearEraContract.BookmarkEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Сообщите всем listeners, что данные по указанному URI изменились
        getContext().getContentResolver().notifyChange(uri, null);

        // Вернуть новый URI с добавленным ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Вставьте новую директорию с заданными значениями ContentValues в базу данных.
     */
    private Uri insertDirectory(Uri uri, ContentValues values) {
        // Проверка данных
        if (!isValidDirectoryEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(HearEraContract.DirectoryEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Сообщите всем listeners, что данные по указанному URI изменились
        getContext().getContentResolver().notifyChange(uri, null);

        // Вернуть новый URI с добавленным ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Удалить данные при заданных аргументах выбора и выбора.
     */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        // Получить доступную для записи базу данных
        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        final int match = sUriMatcher.match(uri);
        ArrayList<Long> ids;
        switch (match) {
            case DIRECTORY:
                //  Удалить соответствующие альбомы
                ids = getAffectedIds(HearEraContract.DirectoryEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = HearEraContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(HearEraContract.AlbumEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Удалить все строки, соответствующие аргументам выбора и выбора
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(HearEraContract.DirectoryEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM:
                // Удалить соответствующие аудиофайлы
                ids = getAffectedIds(HearEraContract.AlbumEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = HearEraContract.AudioEntry.COLUMN_ALBUM + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(HearEraContract.AudioEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Удалить все строки, соответствующие аргументам выбора и выбора
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(HearEraContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO:
                // Удалить соответствующие закладки для каждого удаленного аудиофайла
                ids = getAffectedIds(HearEraContract.AudioEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(HearEraContract.BookmarkEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Удалить все строки, соответствующие аргументам выбора и выбора
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(HearEraContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case BOOKMARK:
                // Удалить все строки, соответствующие аргументам выбора и выбора
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(HearEraContract.BookmarkEntry.TABLE_NAME, selection, selectionArgs);
            case DIRECTORY_ID:
                //Удалить одну строку, заданную ID в URI
                long directoryId = ContentUris.parseId(uri);
                selection = HearEraContract.DirectoryEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(directoryId)};

                //  Удалить соответствующие альбомы
                String selectionAlbum = HearEraContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
                delete(HearEraContract.AlbumEntry.CONTENT_URI, selectionAlbum, selectionArgs);

                // Отправить уведомление об изменении
                getContext().getContentResolver().notifyChange(uri, null);

                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(HearEraContract.DirectoryEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM_ID:
                //Удалить одну строку, заданную ID в URI
                long albumId = ContentUris.parseId(uri);
                selection = HearEraContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(albumId)};

                // Удалить соответствующие аудиофайлы
                String selectionAudioFile = HearEraContract.AudioEntry.COLUMN_ALBUM + "=?";
                delete(HearEraContract.AudioEntry.CONTENT_URI, selectionAudioFile, selectionArgs);

                // Отправить уведомление об изменении
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(HearEraContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO_ID:
                //Удалить одну строку, заданную ID в URI
                long audioId = ContentUris.parseId(uri);
                selection = HearEraContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(audioId)};

                // Удалить соответствующие закладки
                String selectionBookmark = HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
                delete(HearEraContract.BookmarkEntry.CONTENT_URI, selectionBookmark, selectionArgs);

                // Отправить уведомление об изменении
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(HearEraContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case BOOKMARK_ID:
                //Удалить одну строку, заданную ID в URI
                selection = HearEraContract.BookmarkEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Отправить уведомление об изменении
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(HearEraContract.BookmarkEntry.TABLE_NAME, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Обновить все строки, соответствующие аргументам выбора и выбора.
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM:
                return updateAlbum(uri, values, selection, selectionArgs);
            case BOOKMARK:
                return updateBookmark(uri, values, selection, selectionArgs);
            case AUDIO_ID:
                // Обновите одну строку, заданную ID в URI.
                selection = HearEraContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM_ID:
                selection = HearEraContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAlbum(uri, values, selection, selectionArgs);
            case BOOKMARK_ID:
                selection = HearEraContract.BookmarkEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateBookmark(uri, values, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }

    /**
     * Обновите аудиофайлы в базе данных с заданными значениями ContentValues.
     */
    private int updateAudioFile(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Если нет значений для обновления, не пытайтесь обновить базу данных.
        if (values.size() == 0) {
            return 0;
        }

        // Проверка данных
        if (!isValidAudioFileEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Обновить таблицы
        int rowsUpdated = db.update(HearEraContract.AudioEntry.TABLE_NAME, values, selection, selectionArgs);

        // Если 1 или более строк были обновлены, то уведомить всех прослушивателей, что данные в
        //URI изменился
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            if (uri != HearEraContract.AlbumEntry.CONTENT_URI)
                getContext().getContentResolver().notifyChange(HearEraContract.AlbumEntry.CONTENT_URI, null);
        }

        return rowsUpdated;
    }

    /**
     * Обновите альбом в базе данных с заданными значениями ContentValues.
     */
    private int updateAlbum(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Если нет значений для обновления, не пытайтесь обновить базу данных.
        if (values.size() == 0) {
            return 0;
        }

        // Проверка данных
        if (!isValidAlbumEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Обновить таблицы
        int rowsUpdated = db.update(HearEraContract.AlbumEntry.TABLE_NAME, values, selection, selectionArgs);

        // Если 1 или более строк были обновлены, то уведомить всех прослушивателей, что данные в
        //URI изменился
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    /**
     * Обновите альбом в базе данных с заданными значениями ContentValues.
     */
    private int updateBookmark(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Если нет значений для обновления, не пытайтесь обновить базу данных.
        if (values.size() == 0) {
            return 0;
        }

        // Проверка данных
        if (!isValidBokomarkEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Получить доступную для записи базу данных
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Обновить таблицы
        int rowsUpdated = db.update(HearEraContract.BookmarkEntry.TABLE_NAME, values, selection, selectionArgs);

        // Если 1 или более строк были обновлены, то уведомить всех прослушивателей, что данные в
        // данный URI изменился
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    /**
     * Проверка ContentValues
     */
    private boolean isValidAudioFileEntry(ContentValues values) {
        // Проверьте, будет ли обновлен заголовок и что новый заголовок не является нулевым.
        if (values.containsKey(HearEraContract.AudioEntry.COLUMN_TITLE)) {
            String val = values.getAsString(HearEraContract.AudioEntry.COLUMN_TITLE);
            if (val == null) {
                return false;
            }
        }
        // Проверьте, будет ли обновляться альбом и не является ли новое название нулевым.
        if (values.containsKey(HearEraContract.AudioEntry.COLUMN_ALBUM)) {
            String val = values.getAsString(HearEraContract.AudioEntry.COLUMN_ALBUM);
            if (val == null) {
                return false;
            }
        }
        //Проверьте, будет ли обновляться время и не является ли оно нулевым.
        if (values.containsKey(HearEraContract.AudioEntry.COLUMN_TIME)) {
            String val = values.getAsString(HearEraContract.AudioEntry.COLUMN_TIME);
            if (val == null) {
                return false;
            }
        }
        // Проверьте, будет ли обновляться время и не является ли оно нулевым.
        if (values.containsKey(HearEraContract.AudioEntry.COLUMN_COMPLETED_TIME)) {
            String val = values.getAsString(HearEraContract.AudioEntry.COLUMN_COMPLETED_TIME);
            return val != null;
        }
        return true;
    }


    /**
     * проверка ContentValues
     */
    private boolean isValidAlbumEntry(ContentValues values) {
        // Проверьте, будет ли обновлен заголовок и что новый заголовок не является нулевым.
        if (values.containsKey(HearEraContract.AlbumEntry.COLUMN_TITLE)) {
            String val = values.getAsString(HearEraContract.AlbumEntry.COLUMN_TITLE);
            return val != null;
        }
        return true;
    }


    /**
     * проверка ContentValues
     */
    private boolean isValidBokomarkEntry(ContentValues values) {
        // Проверьте, будет ли обновлен заголовок и что новый заголовок не является нулевым.
        if (values.containsKey(HearEraContract.BookmarkEntry.COLUMN_TITLE)) {
            String val = values.getAsString(HearEraContract.BookmarkEntry.COLUMN_TITLE);
            if (val == null) {
                return false;
            }
        }
        // Проверьте, будет ли обновлена позиция и что новая позиция не является нулевой.
        if (values.containsKey(HearEraContract.BookmarkEntry.COLUMN_POSITION)) {
            String val = values.getAsString(HearEraContract.BookmarkEntry.COLUMN_POSITION);
            if (val == null) {
                return false;
            }
        }
        // Проверьте, будет ли обновлен идентификатор аудиофайла и что новый идентификатор не равен нулю.
        if (values.containsKey(HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE)) {
            String val = values.getAsString(HearEraContract.BookmarkEntry.COLUMN_AUDIO_FILE);
            return val != null;
        }
        return true;
    }

    /**
     * проверка ContentValues
     */
    private boolean isValidDirectoryEntry(ContentValues values) {
        // Проверьте, будет ли обновлен заголовок и что новый заголовок не является нулевым.
        if (values.containsKey(HearEraContract.DirectoryEntry.COLUMN_PATH)) {
            String val = values.getAsString(HearEraContract.DirectoryEntry.COLUMN_PATH);
            if (val == null) {
                return false;
            }
            File file = new File(val);
            return file.exists() && file.isDirectory();
        }
        return true;
    }

    /*
     * Возвращает идентификаторы элементов в заданных таблицах, на которые влияет данный запрос.
     */
    private ArrayList<Long> getAffectedIds(Uri uri, String selection, String[] selectionArgs) {
        ArrayList<Long> ids = new ArrayList<>();

        // Определить, какие таблицы запрашиваются
        String column;
        if (uri == HearEraContract.DirectoryEntry.CONTENT_URI) {
            column = HearEraContract.DirectoryEntry._ID;
        } else if (uri == HearEraContract.AlbumEntry.CONTENT_URI) {
            column = HearEraContract.AlbumEntry._ID;
        } else if (uri == HearEraContract.AudioEntry.CONTENT_URI) {
            column = HearEraContract.AudioEntry._ID;
        } else if (uri == HearEraContract.BookmarkEntry.CONTENT_URI) {
            column = HearEraContract.BookmarkEntry._ID;
        } else {
            return ids;
        }

        // Запросить базу данных
        Cursor c = query(uri, new String[]{column}, selection, selectionArgs, null);

        if (c == null) {
            return ids;
        } else if (c.getCount() < 1) {
            c.close();
            return ids;
        }

        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndex(column));
            ids.add(id);
        }
        c.close();

        return ids;
    }
}
