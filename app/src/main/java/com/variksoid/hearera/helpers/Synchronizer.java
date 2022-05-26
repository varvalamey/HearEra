package com.variksoid.hearera.helpers;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.listeners.SynchronizationStateListener;
import com.variksoid.hearera.models.Album;
import com.variksoid.hearera.models.AudioFile;
import com.variksoid.hearera.models.Directory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Synchronizer {
    private final Context mContext;
    private final SharedPreferences mPrefManager;
    private SynchronizationStateListener mListener = null;

    public Synchronizer(Context context) {
        mContext = context;
        mPrefManager = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setListener(SynchronizationStateListener listener) {
        mListener = listener;
    }

    /*
     * Вставьте новый каталог в базу данных и соответственно добавьте содержащиеся в нем альбомы и аудиофайлы.
     */
    public void addDirectory(Directory directory) {
        directory.insertIntoDB(mContext);
        updateAlbumTable(directory);
    }

    /*
     * Для каждого каталога в базе данных обновляются альбомы в соответствии с текущим состоянием файловой системы.
     */
    public void updateDBTables() {
        ArrayList<Directory> directories = Directory.getDirectories(mContext);
        for (Directory directory : directories) {
            updateAlbumTable(directory);
        }
    }

    /*
     * Обновить базу таблицы альбомов, если список каталогов в выбранном каталоге не
     * совпадать с записями в альбомах и таблицах
     */
    private void updateAlbumTable(Directory directory) {
        // Фильтр, чтобы получить все подкаталоги в каталоге
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);
            // Отображать только файлы, которые доступны для чтения, и каталоги, а не скрытые,
            // если не установлен соответствующий параметр.
            return sel.canRead() && sel.isDirectory() && (showHidden || !sel.getName().startsWith("."));
        };

        ArrayList<String> newAlbumPaths = new ArrayList<>();
        File dir = new File(directory.getPath());
        if (dir.exists() && dir.isDirectory()) {
            if (directory.getType() == Directory.Type.PARENT_DIR) {
                // Добавить все подкаталоги, если каталог является родительским каталогом
                String[] subDirArr = dir.list(filter);
                for (String subDirString : subDirArr) {
                    String absolutePath = new File(directory.getPath(), subDirString).getAbsolutePath();
                    newAlbumPaths.add(absolutePath);
                }
            } else if (dir.canRead() && (showHidden || !dir.getName().startsWith("."))) {
                // Добавить каталог, если он является подкаталогом
                newAlbumPaths.add(dir.getAbsolutePath());
            }
        }

        LinkedHashMap<String, Album> oldAlbumPaths = new LinkedHashMap<>();
        ArrayList<Album> albums = Album.getAllAlbumsInDirectory(mContext, directory.getID());
        for (Album album : albums) {
            String path = album.getPath();
            oldAlbumPaths.put(path, album);
        }

        // Вставка новых альбомов в базу данных
        for (String newAlbumPath : newAlbumPaths) {
            long id;
            if (!oldAlbumPaths.containsKey(newAlbumPath)) {
                String albumTitle = new File(newAlbumPath).getName();
                Album album = new Album(albumTitle, directory);
                id = album.insertIntoDB(mContext);
            } else {
                Album album = oldAlbumPaths.get(newAlbumPath);
                id = album.getID();

                // Обновить путь обложки
                String oldCoverPath = album.getRelativeCoverPath();
                String newCoverPath = album.updateAlbumCover();
                if (newCoverPath != null && (oldCoverPath == null || !oldCoverPath.equals(newCoverPath))) {
                    album.updateInDB(mContext);
                }

                oldAlbumPaths.remove(newAlbumPath);
            }
            updateAudioFileTable(newAlbumPath, id);
        }

        // Удалить отсутствующие или скрытые каталоги из базы данных
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        for (String path : oldAlbumPaths.keySet()) {
            String directoryName = new File(path).getName();
            if (!keepDeleted || (!showHidden && directoryName.startsWith("."))) {
                // Удалить альбом в таблице
                long id = oldAlbumPaths.get(path).getID();
                Uri uri = ContentUris.withAppendedId(HearEraContract.AlbumEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
        if (mListener != null) {
            mListener.onSynchronizationFinished();
        }
    }


    /*
     * Обновить таблицу аудиофайлов, если список аудиофайлов в каталоге альбома не
     * Соответствовать записям таблицы аудиофайлов
     */
     private void updateAudioFileTable(String albumPath, long albumId) {
        // Получить все аудиофайлы в альбоме.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);

            // Не показывать файлы, начинающиеся с точки (скрытые файлы), если этот параметр не установлен
            boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
            if (!showHidden && sel.getName().startsWith(".")) {
                return false;
            }

            // Выводить только файлы, которые доступны для чтения, и аудиофайлы
            String[] supportedFormats = {".mp3", ".wma", ".ogg", ".wav", ".flac", ".m4a", ".m4b", ".aac", ".3gp", ".gsm", ".mid", ".mkv", ".opus"};
            for (String format : supportedFormats) {
                if (sel.getName().endsWith(format)) return true;
            }
            return false;
        };

        // Получить все файлы в каталоге альбома.
        String[] fileList;
        File albumDir = new File(albumPath);

        if (albumDir.exists()) {
            fileList = albumDir.list(filter);
        } else {
            fileList = new String[]{};
        }

        if (fileList == null) return;

        ArrayList<AudioFile> audioFiles = AudioFile.getAllAudioFilesInAlbum(mContext, albumId, null);
        LinkedHashMap<String, AudioFile> audioTitles = new LinkedHashMap<>();
         for (AudioFile audioFile : audioFiles) {
             audioTitles.put(audioFile.getTitle(), audioFile);
         }

         // Вставить новые файлы в базу
        String errorString = null;
        for (String audioFileName : fileList) {
            if (!audioTitles.containsKey(audioFileName)) {
                AudioFile audioFile = new AudioFile(mContext, audioFileName, albumId);
                long id = audioFile.insertIntoDB(mContext);
                if (id == -1) errorString = albumPath + "/" + audioFileName;
            } else {
                audioTitles.remove(audioFileName);
            }
        }
        if (errorString != null) {
            errorString = mContext.getResources().getString(R.string.audio_file_error, errorString);
            Toast.makeText(mContext.getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
            return;
        }

        // Удалить отсутствующие или скрытые аудиофайлы из базы данных
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        for (String title : audioTitles.keySet()) {
            if (!keepDeleted || (!showHidden && title.startsWith("."))) {
                long id = audioTitles.get(title).getID();
                Uri uri = ContentUris.withAppendedId(HearEraContract.AudioEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
    }
}
