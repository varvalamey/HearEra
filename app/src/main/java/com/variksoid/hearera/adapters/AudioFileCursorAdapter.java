package com.variksoid.hearera.adapters;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.models.AudioFile;
import com.variksoid.hearera.utils.StorageUtil;
import com.variksoid.hearera.utils.Utils;

import java.io.File;
import java.util.ArrayList;

/**
 * CursorAdapter for the ListView in the Album Activity
 */

public class AudioFileCursorAdapter extends CursorAdapter {

    private final Context mContext;
    private final MediaMetadataRetriever mMetadataRetriever;
    private final SharedPreferences mPrefs;

    public AudioFileCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mMetadataRetriever = new MediaMetadataRetriever();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.audio_file_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Получение пути к аудиофайлу
        long audioID = cursor.getLong(cursor.getColumnIndexOrThrow(HearEraContract.AudioEntry._ID));
        AudioFile audioFile = AudioFile.getAudioFileById(mContext, audioID);

        // Получите заголовок текущего аудиофайла и установите этот текст в titleTV
        TextView titleTV = view.findViewById(R.id.audio_file_item_title);
        String title = "";
        boolean titleFromMetadata = mPrefs.getBoolean(mContext.getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(mContext.getString(R.string.settings_title_from_metadata_default)));
        if (titleFromMetadata && audioFile != null && new File(audioFile.getPath()).exists()) {
            mMetadataRetriever.setDataSource(audioFile.getPath());
            title = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (title == null || title.isEmpty()) {
            // Также используйте имя файла, если аудиофайл не имеет заголовка метаданных.
            assert audioFile != null;
            title = audioFile.getTitle();
        }
        titleTV.setText(title);

        // Получите завершенное время и полное время текущего аудиофайла и установите для этого текста значение durationTV.
        TextView durationTV = view.findViewById(R.id.audio_file_item_duration);
        boolean progressInPercent = mPrefs.getBoolean(mContext.getString(R.string.settings_progress_percentage_key), Boolean.getBoolean(mContext.getString(R.string.settings_progress_percentage_default)));

        String timeStr;
        if (progressInPercent) {
            int percent = Math.round(((float) audioFile.getCompletedTime() / audioFile.getTime()) * 100);
            timeStr = mContext.getResources().getString(R.string.time_completed_percent, percent);

        } else {
            String completedTimeStr = Utils.formatTime(audioFile.getCompletedTime(), audioFile.getTime());
            String durationStr = Utils.formatTime(audioFile.getTime(), audioFile.getTime());
            timeStr = mContext.getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        }
        durationTV.setText(timeStr);

        //Получите путь к миниатюре текущего рецепта и установите src представления изображения
        ImageView thumbnailIV = view.findViewById(R.id.audio_file_item_thumbnail);

        // Установите миниатюру состояния звука на воспроизведение, завершение, паузу или не начато (= прозрачное)
        boolean darkTheme = mPrefs.getBoolean(mContext.getString(R.string.settings_dark_key), Boolean.getBoolean(mContext.getString(R.string.settings_dark_default)));
        if (darkTheme) {
            thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked_dark_theme);
        } else {
            thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked);
        }
        if (isCurrentItemActive(audioID)) {
            thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else if (audioFile.getCompletedTime() >= audioFile.getTime() && audioFile.getTime() != 0) {
            thumbnailIV.setImageResource(R.drawable.ic_checked);
        } else if (audioFile.getCompletedTime() > 0) {
            thumbnailIV.setImageResource(R.drawable.ic_paused);
        } else {
            thumbnailIV.setImageDrawable(null);
        }

        // Показать удаляемое изображение, если файл больше не существует
        ImageView deletableIV = view.findViewById(R.id.audio_file_item_deletable_img);
        if (!(new File(audioFile.getPath())).exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }

    /*
     * Проверьте, запущена ли служба для текущего аудиофайла.
     */
    private boolean isCurrentItemActive(long audioId) {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<Long> audioIdList = new ArrayList<>(storage.loadAudioIds());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioIdList.size() && audioIndex != -1) {
                // Индекс находится в допустимом диапазоне
                long activeAudioId = audioIdList.get(audioIndex);
                return activeAudioId == audioId;
            }
        }
        return false;
    }
}
