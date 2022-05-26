package com.variksoid.hearera.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.variksoid.hearera.models.Album;
import com.variksoid.hearera.models.AudioFile;
import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.utils.BitmapUtils;
import com.variksoid.hearera.utils.DBAccessUtils;
import com.variksoid.hearera.utils.StorageUtil;
import com.variksoid.hearera.utils.Utils;

import java.io.File;
import java.util.ArrayList;

/**
 * CursorAdapter for the ListView in the Main Activity
 */

public class AlbumCursorAdapter extends CursorAdapter {
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final LruCache<String, Bitmap> mImageCache;

    public AlbumCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
        // Получить базовую директорию из shared preferences.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Установка the image cache
        // See https://developer.android.com/topic/performance/graphics/cache-bitmap
        // Получите максимальную доступную память Java VM. Хранится в килобайтах,
        // так как LruCache принимает целое число в своем конструкторе.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        //Используйте часть доступной памяти для этого кэша памяти.
        final int cacheSize = maxMemory / 12;

        mImageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // Размер кэша измеряется в килобайтах, а не в количестве элементов.
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.album_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Получите название текущего альбома и установите этот текст в titleTV
        TextView titleTV = view.findViewById(R.id.audio_storage_item_title);
        titleTV.setSelected(true);
        String title = cursor.getString(cursor.getColumnIndexOrThrow(HearEraContract.AlbumEntry.COLUMN_TITLE));
        titleTV.setText(title);

        // Получить прогресс этого альбома и обновить view
        TextView progressTV = view.findViewById(R.id.album_info_time_album);
        int id = cursor.getInt(cursor.getColumnIndexOrThrow(HearEraContract.AlbumEntry._ID));
        int[] times = DBAccessUtils.getAlbumTimes(context, id);
        String timeStr = Utils.getTimeString(context, times[0], times[1]);
        progressTV.setText(timeStr);

        // Получите путь к миниатюре текущего альбома и установите src представления изображения
        ImageView thumbnailIV = view.findViewById(R.id.audio_storage_item_thumbnail);

        int albumId = cursor.getInt(cursor.getColumnIndexOrThrow(HearEraContract.AlbumEntry._ID));
        Album album = Album.getAlbumByID(mContext, albumId);
        if (isCurrentItemActive(albumId)) {
            boolean darkTheme = mPrefs.getBoolean(mContext.getString(R.string.settings_dark_key), Boolean.getBoolean(mContext.getString(R.string.settings_dark_default)));
            if (darkTheme) {
                thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked_dark_theme);
            } else {
                thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked);
            }
            thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else {
            thumbnailIV.setBackground(null);
            assert album != null;
            String path = album.getCoverPath();
            int reqSize = mContext.getResources().getDimensionPixelSize(R.dimen.album_item_height);
            if (path != null) {
                if (new File(path).exists()) {
                    Bitmap storedImage = getBitmapFromMemCache(path);
                    if (storedImage != null) {
                        thumbnailIV.setImageBitmap(storedImage);
                    } else {
                        Bitmap image = BitmapUtils.decodeSampledBitmap(path, reqSize, reqSize);
                        thumbnailIV.setImageBitmap(image);
                        addBitmapToMemoryCache(path, image);
                    }
                } else {
                    setEmptyCoverImage(thumbnailIV, reqSize);
                }
            } else {
                setEmptyCoverImage(thumbnailIV, reqSize);
            }
        }

        // Показать удаляемое изображение, если файл больше не существует
        ImageView deletableIV = view.findViewById(R.id.album_item_deletable_img);
        assert album != null;
        if (!(new File(album.getPath())).exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }

    private void setEmptyCoverImage(ImageView iv, int reqSize) {
        String imageKey = String.valueOf(R.drawable.empty_cover_grey_blue);
        Bitmap storedImage = getBitmapFromMemCache(imageKey);
        if (storedImage != null) {
            iv.setImageBitmap(storedImage);
        } else {
            Bitmap image = BitmapUtils.decodeSampledBitmap(mContext.getResources(), R.drawable.empty_cover_grey_blue, reqSize, reqSize);
            iv.setImageBitmap(image);
            addBitmapToMemoryCache(imageKey, image);
        }
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mImageCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mImageCache.get(key);
    }

    /*
     * Проверяем, запущена ли служба для аудиофайла из текущего альбома.
     */
    private boolean isCurrentItemActive(int albumId) {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<Long> audioIdList = new ArrayList<>(storage.loadAudioIds());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioIdList.size() && audioIndex != -1) {
                // Индекс находится в допустимом диапазоне
                long activeAudioId = audioIdList.get(audioIndex);
                AudioFile activeAudio;
                try {
                    activeAudio = AudioFile.getAudioFileById(mContext, activeAudioId);
                } catch (SQLException e) {
                    return false;
                }
                long playingAlbumId = 0;
                if (activeAudio != null) {
                    playingAlbumId = activeAudio.getAlbumId();
                }
                return playingAlbumId == albumId;
            }
        }
        return false;
    }
}
