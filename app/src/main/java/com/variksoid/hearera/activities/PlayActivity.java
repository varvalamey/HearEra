package com.variksoid.hearera.activities;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.variksoid.hearera.R;
import com.variksoid.hearera.adapters.BookmarkCursorAdapter;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.helpers.SleepTimer;
import com.variksoid.hearera.models.AudioFile;
import com.variksoid.hearera.models.Bookmark;
import com.variksoid.hearera.services.MediaPlayerService;
import com.variksoid.hearera.utils.BitmapUtils;
import com.variksoid.hearera.utils.SkipIntervalUtils;
import com.variksoid.hearera.utils.StorageUtil;
import com.variksoid.hearera.utils.Utils;

import java.util.ArrayList;


public class PlayActivity extends AppCompatActivity {

    public static final String BROADCAST_PLAY_AUDIO = "com.variksoid.hearera.PlayAudio";
    public static final String BROADCAST_PAUSE_AUDIO = "com.variksoid.hearera.PauseAudio";

    private MediaPlayerService mPlayer;
    boolean serviceBound = false;
    boolean mStopServiceOnDestroy = false;
    BroadcastReceiver mPlayStatusReceiver;
    BroadcastReceiver mNewAudioFileReceiver;
    MediaMetadataRetriever mMetadataRetriever;
    SleepTimer mSleepTimer = null;
    SensorManager mSensorManager;
    StorageUtil mStorage;

    // переменные аудиофайла
    AudioFile mAudioFile;
    int mAudioIndex;
    ArrayList<Long> mAudioIdList;

    // Views
    ImageView mCoverIV;
    TextView mTitleTV;
    TextView mAlbumTV;
    ImageView mPlayIV;
    ImageView mBackward1IV;
    ImageView mForward1IV;
    TextView mBackward1TV;
    TextView mForward1TV;
    SeekBar mSeekBar;
    TextView mCompletedTimeTV;
    TextView mTimeTV;
    TextView mSleepCountDownTV;

    // SeekBar переменные
    Handler mHandler;
    Runnable mRunnable;

    // таймер сна
    int mLastSleepTime;

    // Bookmark Adapter и Bookmark ListView
    BookmarkCursorAdapter mBookmarkAdapter;
    ListView mBookmarkListView;

    // Флаги
    private boolean mCoverFromMetadata;
    private boolean mTitleFromMetadata;
    private boolean mCoverBelowTrackData;
    boolean mDarkTheme;

    // переференсы
    SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // достаем uri выбранного аудио
        long currAudioId = getIntent().getLongExtra(getString(R.string.curr_audio_id), -1);

        mCoverIV = findViewById(R.id.play_cover);
        mTitleTV = findViewById(R.id.play_audio_file_title);
        mAlbumTV = findViewById(R.id.play_album_title);

        mPlayIV = findViewById(R.id.play_play);

        mBackward1IV = findViewById(R.id.backward_2_iv);
        mForward1IV = findViewById(R.id.forward_1_iv);
        mBackward1TV = findViewById(R.id.backward_2_tv);
        mForward1TV = findViewById(R.id.forward_1_tv);


        mSeekBar = findViewById(R.id.play_seekbar);
        mCompletedTimeTV = findViewById(R.id.play_completed_time);
        mTimeTV = findViewById(R.id.play_time);
        mSleepCountDownTV = findViewById(R.id.play_sleep_time);

        // достаем преференсы
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        mTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        mCoverBelowTrackData = mSharedPreferences.getBoolean(getString(R.string.settings_display_cover_below_track_data_key), Boolean.getBoolean(getString(R.string.settings_display_cover_below_track_data_default)));
        mLastSleepTime = mSharedPreferences.getInt(getString(R.string.preference_last_sleep_key), Integer.parseInt(getString(R.string.preference_last_sleep_val)));
        mDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));

        mAudioFile = AudioFile.getAudioFileById(this, currAudioId);
        mMetadataRetriever = new MediaMetadataRetriever();

        mStorage = new StorageUtil(getApplicationContext());

        setNewAudioFile();
        setAlbumCover();

        mHandler = new Handler();
        // привязать сервис если он уже запущен
        if (!serviceBound && Utils.isMediaPlayerServiceRunning(this)) {
            bindService();
        }
        // хранение очереди аудиофайлов
        if (!serviceBound) {
            storeAudioFiles();
        }

        // BroadcastReceivers, все относятся к сервисным событиям
        mPlayStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
                Log.e("PlayActivity", "Received Play Status Broadcast " + s);
                if (s != null) {
                    switch (s) {
                        case MediaPlayerService.MSG_PLAY:
                            mPlayIV.setImageResource(R.drawable.pause_button);
                            if (!serviceBound) {
                                bindService();
                            }
                            break;
                        case MediaPlayerService.MSG_PAUSE:
                        case MediaPlayerService.MSG_STOP:
                            mPlayIV.setImageResource(R.drawable.play_button);
                            break;
                    }
                }
            }
        };

        mNewAudioFileReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int audioIndex = intent.getIntExtra(MediaPlayerService.SERVICE_MESSAGE_NEW_AUDIO, -1);
                if (audioIndex > -1) {
                    loadAudioFile(audioIndex);
                    initializeSeekBar();
                }
            }
        };

        mPlayIV.setOnClickListener(view -> {
            // Избежание "приложение не отвечает" ошибки при нажатии плей на уже проигранном файле
            // путем не запускания MediaPlayerService в этом случае
            boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));
            if (mPlayer == null && mAudioFile.getCompletedTime() == mAudioFile.getTime() && !autoplay) {
                return;
            }

            if (mPlayer == null || !mPlayer.isPlaying()) {
                playAudio();
            } else {
                pauseAudio();
            }
        });

        initSkipButtons();


        mBackward1IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_backward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
            skipBackward(skipInterval);
        });
        mForward1IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_forward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
            skipForward(skipInterval);
        });


        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateAudioCompletedTime(progress * 1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Установка переменных оносщихся к таймеру сна
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Регистрация BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mNewAudioFileReceiver, new IntentFilter(MediaPlayerService.SERVICE_NEW_AUDIO));
        // Необходимо,чтобы это был ресвер глобальных бродкастов, так как deleteIntent бродкастится
        // фреймворком уведомлений Android
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));

        boolean immediatePlayback = mSharedPreferences.getBoolean(getString(R.string.settings_immediate_playback_key), Boolean.getBoolean(getString(R.string.settings_immediate_playback_default)));
        if (immediatePlayback) playAudio();

        // Показывать обложку альбома под информацией о треке если выбран такой преференс в настройках
        if (mCoverBelowTrackData) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mCoverIV.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, mAlbumTV.getId());
            mCoverIV.setLayoutParams(params);
        }
    }

    void initSkipButtons() {

        int skipIntervalBackwardButton1 = mSharedPreferences.getInt(getString(R.string.settings_backward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
        int skipIntervalForwardButton1 = mSharedPreferences.getInt(getString(R.string.settings_forward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));

        // Установить инервал пропуска в тексте skip buttons

       mBackward1TV.setText(String.valueOf(skipIntervalBackwardButton1));
        mForward1TV.setText(String.valueOf(skipIntervalForwardButton1));

        // Установить иконку кнопки если кнопка имеет макчмальный интервал установленным
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalBackwardButton1)) {
            mBackward1IV.setImageResource(R.drawable.previous_button);
           mBackward1TV.setVisibility(View.INVISIBLE);
        } else {
            mBackward1IV.setImageResource(R.drawable.backward_button);
           mBackward1TV.setVisibility(View.VISIBLE);
        }
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalForwardButton1)) {
            mForward1IV.setImageResource(R.drawable.next_button);
            mForward1TV.setVisibility(View.INVISIBLE);
        } else {
            mForward1IV.setImageResource(R.drawable.forward_button);
            mForward1TV.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e("PlayActivity", "OnStart called");

        if (mPlayer != null) {
            mAudioFile = mPlayer.getCurrentAudioFile();
            setNewAudioFile();
            setAlbumCover();
        }
        initializeSeekBar();
    }

    @Override
    protected void onRestart() {
        // Перезапустить если тема, getCoverFromMetadata, getTitleFromMetadata, или coverImageBelowTrackData были изменены
        boolean currentDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        boolean currentGetCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        boolean currentGetTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        boolean currentCoverImageBelowTrackData = mSharedPreferences.getBoolean(getString(R.string.settings_display_cover_below_track_data_key), Boolean.getBoolean(getString(R.string.settings_display_cover_below_track_data_default)));
        if (mDarkTheme != currentDarkTheme || mCoverFromMetadata != currentGetCoverFromMetadata || mTitleFromMetadata != currentGetTitleFromMetadata || mCoverBelowTrackData != currentCoverImageBelowTrackData) {
            recreate();
        }
        initSkipButtons();

        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            Log.e("PlayActivity", "Unbinding Service");
            unbindService(serviceConnection);
        }
        if (mStopServiceOnDestroy && mPlayer != null) {
            Log.e("PlayActivity", "Stopping Service");
            mPlayer.stopSelf();
        }

        // Остановка runnable от проигрывания на заднем плане
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNewAudioFileReceiver);
        unregisterReceiver(mRemoveNotificationReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sleep_timer:
                showSleepTimerDialog();
                return true;
            case R.id.menu_goto:
                showGoToDialog();
                return true;
            case R.id.menu_set_bookmark:
                boolean quickBookmark = mSharedPreferences.getBoolean(getString(R.string.settings_quick_bookmark_key), Boolean.getBoolean(getString(R.string.settings_quick_bookmark_default)));
                if (quickBookmark) {
                    setBookmarkWithoutConfirmation();
                } else {
                    showSetBookmarkDialog(null);
                }
                return true;
            case R.id.menu_show_bookmarks:
                showShowBookmarksDialog();
                return true;
            case R.id.menu_playback_speed:
                showPlaybackSpeedDialog();
                return true;
            case R.id.menu_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return (super.onOptionsItemSelected(item));
    }


    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    // Привязка данного клиента к AudioPlayer сервису
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("PlayActivity", "OnServiceConnected called");
            // Привязались к LocalService, запускаем IBinder и достаем данные о LocalService
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            serviceBound = true;

            // Выполняем функции которые могут быть запущены только при привязанном сервисе
            // Усанавливаем ImageView проигрывателя
            if (mPlayer.isPlaying()) {
                mPlayIV.setImageResource(R.drawable.pause_button);
            } else {
                mPlayIV.setImageResource(R.drawable.play_button);
            }

            // Привязываем SleepTimerTV если активирован таймер сна
            if (mPlayer.getSleepTimer() != null) {
                mPlayer.getSleepTimer().setNewSleepCountDownTV(mSleepCountDownTV);
            } else if (mSleepTimer != null) {
                mPlayer.connectSleepTimer(mSleepTimer);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void startService() {
        // проверяем если сервис активен
        if (!serviceBound) {
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(playerIntent);
            } else {
                startService(playerIntent);
            }
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void bindService() {
        Intent playerIntent = new Intent(this, MediaPlayerService.class);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void playAudio() {
        // Отправка бродкаста в сервис о том, что аудио должно играть
        startService();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_PLAY_AUDIO));
        mStopServiceOnDestroy = false;
    }

    private void pauseAudio() {
        // Отправка бродкаста в сервис о том, что аудио должно быть на паузе
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_PAUSE_AUDIO));
    }

    private void storeAudioFiles() {
        // Хранение Serializable audioList в SharedPreferences
        String sortOrder = "CAST(" + HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_TITLE + ") ASC";

        ArrayList<AudioFile> audioList = AudioFile.getAllAudioFilesInAlbum(this, mAudioFile.getAlbumId(), sortOrder);
        mAudioIdList = new ArrayList<>();
        for (AudioFile audioFile : audioList) {
            mAudioIdList.add(audioFile.getID());
        }

        mAudioIndex = mAudioIdList.indexOf(mAudioFile.getID());
        mStorage.storeAudioIds(mAudioIdList);
        mStorage.storeAudioIndex(mAudioIndex);
        mStorage.storeAudioId(mAudioFile.getID());
    }

    private void loadAudioFile(int audioIndex) {
        // Загрузка данных из SharedPreferences
        mAudioIndex = audioIndex;
        long audioId = mAudioIdList.get(audioIndex);
        mAudioFile = AudioFile.getAudioFileById(this, audioId);
        setNewAudioFile();
        setAlbumCover();
    }

    /*
     * Отвязка PlayActivity от MediaPlayerService когда юзер убирает уведомление
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("PlayActivity", "Received broadcast 'remove notification'");
            mStopServiceOnDestroy = true;
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /*
     * Установка views для данного аудофайла
     */
    void setNewAudioFile() {
        // Установка TextViews
        String title = "";
        if (mTitleFromMetadata) {
            mMetadataRetriever.setDataSource(mAudioFile.getPath());
            title = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (title == null || title.isEmpty()) {
            title = mAudioFile.getTitle();
        }
        mTitleTV.setText(title);
        mTimeTV.setText(Utils.formatTime(mAudioFile.getTime(), mAudioFile.getTime()));
        mAlbumTV.setText(mAudioFile.getAlbumTitle());
    }

    /*
     * Установка обложки альбома для данного пути к обложкам
     */
    void setAlbumCover() {
        // Установка обложки как ImageView и названия как TextView
        Point size = new Point();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display;
        int reqSize = getResources().getDimensionPixelSize(R.dimen.default_device_width);
        if (wm != null) {
            display = wm.getDefaultDisplay();
            display.getSize(size);
            reqSize = java.lang.Math.max(size.x, size.y);
        }

        if (mCoverFromMetadata) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(mAudioFile.getPath());

            byte[] coverData = mmr.getEmbeddedPicture();

            // Переопределене массива битов в bitmap
            if (coverData != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(coverData, 0, coverData.length);
                mCoverIV.setImageBitmap(bitmap);
            } else {
                BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
            }
        } else {
            BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
        }

        mCoverIV.setAdjustViewBounds(true);

    }

    /*
     * Инициализация SeekBar
     */
    void initializeSeekBar() {
        // Если другой Runnable уже подключен к хендлеру, то удалить старый
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        mRunnable = new Runnable() {
            boolean firstRun = true;

            @Override
            public void run() {
                if (firstRun) {
                    mSeekBar.setMax(mAudioFile.getTime() / 1000);
                    firstRun = false;
                }
                int currentPosition = getAudioCompletedTime();
                mSeekBar.setProgress(currentPosition / 1000);
                mCompletedTimeTV.setText(Utils.formatTime(currentPosition, mAudioFile.getTime()));
                mHandler.postDelayed(mRunnable, 100);
            }
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    void showSleepTimerDialog() {
        // Установка Views
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_sleep_timer, null);
        final EditText setTime = dialogView.findViewById(R.id.sleep_timer_set_time);
        setTime.setText(String.valueOf(mLastSleepTime));
        setTime.setSelection(setTime.getText().length());
        final Button quickButton0 = dialogView.findViewById(R.id.quick_button_0);
        final Button quickButton1 = dialogView.findViewById(R.id.quick_button_1);
        final Button quickButton2 = dialogView.findViewById(R.id.quick_button_2);
        final Button quickButton3 = dialogView.findViewById(R.id.quick_button_3);
        final Button quickButton4 = dialogView.findViewById(R.id.quick_button_4);

        // Установка alertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(R.string.sleep_timer);
        builder.setMessage(R.string.dialog_msg_sleep_timer);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String minutesString = setTime.getText().toString();
            int minutes;
            if (minutesString.isEmpty()) {
                minutes = 0;
            } else {
                minutes = Integer.parseInt(minutesString);
            }

            if (mPlayer != null) {
                mPlayer.startSleepTimer(minutes, mSleepCountDownTV);
            } else {
                startSleepTimer(minutes);
            }

            // Сохранение выбранного в mSharedPreferences
            mLastSleepTime = minutes;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_last_sleep_key), mLastSleepTime);
            editor.apply();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создать и показать AlertDialog
        AlertDialog alertDialog = builder.create();

        // Установка quick button click listeners
        setQuickButtonClickListener(quickButton0, alertDialog);
        setQuickButtonClickListener(quickButton1, alertDialog);
        setQuickButtonClickListener(quickButton2, alertDialog);
        setQuickButtonClickListener(quickButton3, alertDialog);
        setQuickButtonClickListener(quickButton4, alertDialog);

        alertDialog.show();
    }

    void setQuickButtonClickListener(Button button, Dialog dialog) {
        button.setOnClickListener(v -> {
            int minutes = Integer.parseInt(button.getText().toString());
            if (mPlayer != null) {
                mPlayer.startSleepTimer(minutes, mSleepCountDownTV);
            } else {
                startSleepTimer(minutes);
            }
            dialog.dismiss();
        });
    }

    void startSleepTimer(int minutes) {
        // Получение преференсов аймера сна
        boolean shakeEnabledSetting = mSharedPreferences.getBoolean(getString(R.string.settings_shake_key), Boolean.getBoolean(getString(R.string.settings_shake_default)));
        int shakeSensitivitySetting = mSharedPreferences.getInt(getString(R.string.settings_shake_sensitivity_key), Integer.parseInt(getString(R.string.settings_shake_sensitivity_default)));
        float shakeForceRequired = (100 - shakeSensitivitySetting) / 100f;

        if (mSleepTimer == null) {
            mSleepTimer = new SleepTimer(mSleepCountDownTV, mSensorManager, this);
        }

        // Создание и запуск таймера
        mSleepTimer.createTimer(minutes * 60, shakeEnabledSetting, shakeForceRequired);
        mSleepTimer.startTimer(false);
    }

    /*
     * Показать диалог который позволит пользователю уточнить куда переключать
     */
    void showGoToDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_goto, null);
        builder.setView(dialogView);

        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        int currPos = getAudioCompletedTime();
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setTitle(R.string.go_to);
        builder.setMessage(R.string.dialog_msg_goto);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            try {
                long millis = Utils.getMillisFromString(timeString);
                updateAudioCompletedTime((int) millis);
            } catch (NumberFormatException e) {
                Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создать и показать AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Установить новую закладку на данном времени с дефолтным названием и уведомить пользователя
     * с помощью toast message.
     */
    void setBookmarkWithoutConfirmation() {
        String title = getResources().getString(R.string.untitled_bookmark);
        Bookmark bookmark = new Bookmark(title, getAudioCompletedTime(), mAudioFile.getID());
        bookmark.insertIntoDB(this);
        String timeString = Utils.formatTime(bookmark.getPosition(), 3600000);
        String addedToastMsg = getResources().getString(R.string.bookmark_added_toast, title, timeString);
        Toast.makeText(getApplicationContext(), addedToastMsg, Toast.LENGTH_SHORT).show();
    }

    /*
     * Показываем диалог, который позволяет пользователю указать название для закладки. Пользователь
     * подтверждает что он хочет создать закладку и сохранить ее
     * Если uri нолевой, новая закладка создается. В другом случает тот же uri
     * обнвляется
     */
    void showSetBookmarkDialog(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_bookmark, null);
        builder.setView(dialogView);

        final EditText bookmarkTitleET = dialogView.findViewById(R.id.bookmark_title_et);
        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        long bookmarkID = -1;
        if (uri != null) bookmarkID = ContentUris.parseId(uri);
        final Bookmark bookmark;
        if (bookmarkID >= 0) {
            builder.setTitle(R.string.edit_bookmark);
            // Достать закладку
            bookmark = Bookmark.getBookmarkByID(this, bookmarkID);
            bookmarkTitleET.setText(bookmark.getTitle());
        } else {
            builder.setTitle(R.string.set_bookmark);
            bookmark = new Bookmark("", getAudioCompletedTime(), mAudioFile.getID());
        }

        // Установить text views с данными закладки
        String[] currPosArr = Utils.formatTime(bookmark.getPosition(), 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Пользователь нажал OK так что сохраняем закладку
            String title = bookmarkTitleET.getText().toString();

            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            if (title.isEmpty()) {
                // Достаем дефолтное название
                title = getResources().getString(R.string.untitled_bookmark);
            }
            try {
                long millis = Utils.getMillisFromString(timeString);
                bookmark.setPosition(millis);
                bookmark.setTitle(title);
                if (bookmark.getID() == -1) {
                    // Вставка закладки в таблицу закладок
                    bookmark.insertIntoDB(this);
                    String addedToastMsg = getResources().getString(R.string.bookmark_added_toast, title, timeString);
                    Toast.makeText(getApplicationContext(), addedToastMsg, Toast.LENGTH_SHORT).show();
                } else {
                    // Обновление закладки в таблице закладок
                    bookmark.updateInDB(this);

                    // Перезагрузка ListView
                    Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
                    mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
                    mBookmarkListView.setAdapter(mBookmarkAdapter);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
            }
        });

        if (uri != null) {
            builder.setNeutralButton(R.string.dialog_msg_delete, (dialog, id) -> {
                // Пользователь нажал удалить, так что удаляем закладку
                deleteBookmarkWithConfirmation(uri);
            });
        }

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // Пользовательнажал отменить, так что сворачиваем диалог
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создать и показать AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Показать диалог который показывает все закладки в данном аудио файле. Если пользователь нажимает на
     * значок закладки, то данное время прослушивания устанавливается как закладка.
     */
    void showShowBookmarksDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_show_bookmarks, null);
        builder.setView(dialogView);

        mBookmarkListView = dialogView.findViewById(R.id.list_bookmarks);

        // Достать cursor adapter и установить его в list view
        Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
        mBookmarkAdapter = new BookmarkCursorAdapter(this, c, mAudioFile.getTime());
        mBookmarkListView.setAdapter(mBookmarkAdapter);

        // Установить EmptyView для ListView
        TextView emptyTV = dialogView.findViewById(R.id.emptyView_bookmarks);
        mBookmarkListView.setEmptyView(emptyTV);

        // Наследовать onItemClickListener для list view
        mBookmarkListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Установить курсор на значение времени в выбранной закладке
            TextView positionTV = view.findViewById(R.id.bookmark_position_tv);
            String positionString = positionTV.getText().toString();
            int millis = (int) Utils.getMillisFromString(positionString);
            updateAudioCompletedTime(millis);

            // Уведомить пользователя о прыжке на данное время через toast
            TextView nameTV = view.findViewById(R.id.bookmark_title_tv);
            String name = nameTV.getText().toString();
            String jumpToast = getResources().getString(R.string.jumped_to_bookmark, name, positionString);
            Toast.makeText(getApplicationContext(), jumpToast, Toast.LENGTH_SHORT).show();
        });

        mBookmarkListView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            Uri uri = ContentUris.withAppendedId(HearEraContract.BookmarkEntry.CONTENT_URI, l);
            showSetBookmarkDialog(uri);
            return true;
        });

        builder.setTitle(R.string.bookmarks);
        // Пользователь нажал OK, производим запуск таймера сна

        builder.setPositiveButton(R.string.dialog_msg_close, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создать и показать AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Показать диалог подтверждения удаленя закладк и дать пользователю возможность выбрать удалять ли ее
     */
    void deleteBookmarkWithConfirmation(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_bookmark);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Пользователь нажал ОК, удаляем
            getContentResolver().delete(uri, null, null);

            // Обновляем ListView
            Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
            mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
            mBookmarkListView.setAdapter(mBookmarkAdapter);
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // Пользовательнажал отменить, сворачиваем диалог
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создать и показать AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Показать диалог, позволяющий пользователю выбрать скорость воспроизведения
     */
    void showPlaybackSpeedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_playback_speed, null);
        builder.setView(dialogView);
        builder.setTitle(R.string.playback_speed);

        final TextView playbackSpeedTV = dialogView.findViewById(R.id.playback_speed_tv);
        SeekBar playbackSpeedSB = dialogView.findViewById(R.id.playback_speed_sb);
        float normalSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
        int minSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        playbackSpeedSB.setMax((int)(2.5 * normalSpeed));  // min + max = 0.5 + 2.5 = 3.0 --> max playback speed 3.0
        int currSpeed = mSharedPreferences.getInt(getString(R.string.preference_playback_speed_key), Integer.parseInt(getString(R.string.preference_playback_speed_default)));
        if (currSpeed < minSpeed) {
            // Обеспечить обратную совместимость, если сохраненная скорость находится в диапазоне от 5 до 25.
            currSpeed = currSpeed * 10;
            // Сохранить новую скорочть в shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), currSpeed);
            editor.apply();
        }
        float currSpeedFloat = currSpeed / normalSpeed;
        playbackSpeedTV.setText(getString(R.string.playback_speed_label, currSpeedFloat));
        int progress = getProgressFromPlaybackSpeed(currSpeed);
        playbackSpeedSB.setProgress(progress);
        playbackSpeedSB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int speed = getPlaybackSpeedFromProgress(progress);
                    float speedFloat = (speed / normalSpeed);
                    playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int speed = getPlaybackSpeedFromProgress(seekBar.getProgress());

                // Сохранить новую скорость в shared preferences
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putInt(getString(R.string.preference_playback_speed_key), speed);
                editor.apply();

                // Установить скорость воспроизведения на ту, которую установил пользователь
                float speedFloat = (speed / normalSpeed);
                if (mPlayer != null) {
                    mPlayer.setPlaybackSpeed(speedFloat);
                }
            }
        });

        final ImageView resetIV = dialogView.findViewById(R.id.reset);
        resetIV.setOnClickListener(v -> {
            int speed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
            float speedFloat = (speed / normalSpeed);
            playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));

            // Сохранить новую скорость в shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), speed);
            editor.apply();

            // Set position of seeker to default position 1.0
            playbackSpeedSB.setProgress(getProgressFromPlaybackSpeed(speed));

            // Set playback speed to speed selected by the user
            if (mPlayer != null) {
                mPlayer.setPlaybackSpeed(speedFloat);
            }
        });

        // Создайте и покажите AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    int getPlaybackSpeedFromProgress(int progress) {
        int min = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        return progress + min;
    }

    int getProgressFromPlaybackSpeed(int speed) {
        int min = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        return speed - min;
    }

    void updateAudioCompletedTime(int newTime) {
        if (mPlayer != null) {
            mPlayer.setCurrentPosition(newTime);
        } else {
            // Обновить текущее аудио
            mAudioFile.setCompletedTime(newTime);

            // Обновите столбец completeTime таблицы audiofiles.
            Uri uri = ContentUris.withAppendedId(HearEraContract.AudioEntry.CONTENT_URI, mAudioFile.getID());
            ContentValues values = new ContentValues();
            values.put(HearEraContract.AudioEntry.COLUMN_COMPLETED_TIME, newTime);
            getContentResolver().update(uri, values, null, null);
        }
    }

    int getAudioCompletedTime() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        } else {
            return mAudioFile.getCompletedTime();
        }
    }

    /*
     * Перейти назад: В зависимости от пользовательских настроек либо к предыдущему файлу, либо назад
     * в текущем файле.
     * Если MediaPlayerService уже запущен, используйте функцию MediaPlayerService, в противном случае установите предыдущий
     * файл в PlayActivity.
     */
    void skipBackward(int skipInterval) {
        if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
            if (mPlayer != null) {
                mPlayer.skipToPreviousAudioFile();
            } else {
                if (mAudioIndex > 0) {
                    // Сохранить текущее время завершения аудио
                    updateAudioCompletedTime(mAudioFile.getCompletedTime());
                    // Загрузить новый файл
                    int audioIndex = mAudioIndex - 1;
                    initNewAudioFileViaPlayActivity(audioIndex);
                }
            }
        } else {
            int newTime = Math.max(getAudioCompletedTime() - skipInterval*1000, 0);
            updateAudioCompletedTime(newTime);
        }
    }

    /*
     * Перейти вперед: в зависимости от настроек пользователя либо к следующему файлу, либо вперед в
     * текущем файле.
     * Если MediaPlayerService уже запущен, используйте функцию MediaPlayerService, иначе установите следующий
     * файл в PlayActivity.
     */
    void skipForward(int skipInterval) {
        if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
            if (mPlayer != null) {
                mPlayer.skipToNextAudioFile();
            } else {
                if (mAudioIndex + 1 < mAudioIdList.size()) {
                    // Store current audio completed time
                    updateAudioCompletedTime(mAudioFile.getCompletedTime());
                    // Load new audio file
                    int audioIndex = mAudioIndex + 1;
                    initNewAudioFileViaPlayActivity(audioIndex);
                }
            }
        } else {
            int newTime = Math.min(getAudioCompletedTime() + skipInterval * 1000, mAudioFile.getTime());
            updateAudioCompletedTime(newTime);
        }
    }

    /*
     * Инициализировать аудиофайл с заданным индексом в сохраненном списке AudioFile, когда
     * Служба MediaPlayerService еще не запущена.
     */
    void initNewAudioFileViaPlayActivity(int audioIndex) {
        mAudioIndex = audioIndex;
        loadAudioFile(mAudioIndex);

        // Обновить хранилище
        mStorage.storeAudioIndex(mAudioIndex);
        mStorage.storeAudioId(mAudioFile.getID());

        // Перезапустите аудиофайл с начала, если эта опция выбрана
        boolean restartFromBeginning = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_restart_key), Boolean.getBoolean(getString(R.string.settings_autoplay_restart_default)));
        if (restartFromBeginning) {
            mAudioFile.setCompletedTime(0);
            updateAudioCompletedTime(mAudioFile.getCompletedTime());
        }

        initializeSeekBar();
    }
}
