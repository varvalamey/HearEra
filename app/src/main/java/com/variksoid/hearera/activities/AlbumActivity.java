package com.variksoid.hearera.activities;

import android.Manifest;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.variksoid.hearera.R;
import com.variksoid.hearera.adapters.AudioFileCursorAdapter;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.helpers.Synchronizer;
import com.variksoid.hearera.listeners.PlayStatusChangeListener;
import com.variksoid.hearera.listeners.SynchronizationStateListener;
import com.variksoid.hearera.models.Album;
import com.variksoid.hearera.models.AudioFile;
import com.variksoid.hearera.receivers.PlayStatusReceiver;
import com.variksoid.hearera.services.MediaPlayerService;
import com.variksoid.hearera.utils.BitmapUtils;
import com.variksoid.hearera.utils.DBAccessUtils;
import com.variksoid.hearera.utils.StorageUtil;
import com.variksoid.hearera.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class AlbumActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, PlayStatusChangeListener, SynchronizationStateListener {

    // uri альбома и файл
    private Album mAlbum;

    // переменные базы данных
    private static final int ALBUM_LOADER = 0;
    private AudioFileCursorAdapter mCursorAdapter;

    // переменные разметки
    ListView mListView;
    SwipeRefreshLayout mSwipeRefreshLayout;
    TextView mEmptyTV;
    ImageView mAlbumInfoCoverIV;
    TextView mAlbumInfoTitleTV;
    TextView mAlbumInfoTimeTV;
    FloatingActionButton mPlayPauseFAB;

    // переменные настроек
    SharedPreferences mPrefs;
    boolean mDarkTheme;
    boolean mShowHiddenFiles;

    // переменные для множественного выбора
    ArrayList<Long> mSelectedTracks = new ArrayList<>();
    ArrayList<Long> mTmpSelectedTracks;

    // MediaPlayerService переменные
    private MediaPlayerService mPlayer;
    boolean mServiceBound = false;
    boolean mDoNotBindService = false;
    Handler mHandler;
    Runnable mRunnable;
    int mAlbumLastCompletedTime;
    int mAlbumDuration;
    int mCurrAudioLastCompletedTime;
    long mCurrUpdatedAudioId;

    // Ресивер
    PlayStatusReceiver mPlayStatusReceiver;

    // Синхрон
    private Synchronizer mSynchronizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Получение uri
        long albumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mAlbum = Album.getAlbumByID(this, albumId);

        // Подготовка CursorLoader. Либо переподключение к старому, либо создание нового
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Установка shared preferece
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDarkTheme = mPrefs.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        mShowHiddenFiles = mPrefs.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));

        // Инициализация курсора
        mCursorAdapter = new AudioFileCursorAdapter(this, null);

        // Инициализация синхронайзера
        mSynchronizer = new Synchronizer(this);
        mSynchronizer.setListener(this);

        // Установка views
        mAlbumInfoTitleTV = findViewById(R.id.album_info_title);
        mAlbumInfoTimeTV = findViewById(R.id.album_info_time);
        mAlbumInfoCoverIV = findViewById(R.id.album_info_cover);
        mPlayPauseFAB = findViewById(R.id.play_pause_fab);

        // Используем ListView и CursorAdapter для переработки пространства
        mListView = findViewById(R.id.list_album);
        mListView.setAdapter(mCursorAdapter);

        // Установка EmptyView для ListView
        mEmptyTV = findViewById(R.id.emptyList_album);
        mListView.setEmptyView(mEmptyTV);

        // Наследование onItemClickListener для list view
        mListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Проверка если файл уже существует
            AudioFile audio = AudioFile.getAudioFileById(AlbumActivity.this, rowId);

            if (audio == null || !(new File(audio.getPath())).exists()) {
                Toast.makeText(getApplicationContext(), R.string.play_error, Toast.LENGTH_LONG).show();
                return;
            }

            // Если MediaPlayerService привязан, проверить если проигрываемый файл бын нажат
            // Если нет, открыть новый

            if (mServiceBound && mPlayer.getCurrentAudioFile().getID() != audio.getID()) {
                Log.e("AlbumActivity", "Unbinding Service ");
                unbindService(serviceConnection);
                mServiceBound = false;
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(new Intent(MediaPlayerService.BROADCAST_UNBIND_CURRENT_SERVICE));
                mPlayer.stopSelf();
            }

            // При возвращении к Album или MainActivity  в следующий раз сервис не должен быть привязан
            // (за исключением случая когда уведомление было убрано, тогда флад отмечен 1 в
            // RemoveNotificationReceiver)
            mDoNotBindService = false;
            LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(new Intent(MediaPlayerService.BROADCAST_RESET));

            // Открыть PlayActivity для нажатого файла
            Intent intent = new Intent(AlbumActivity.this, PlayActivity.class);
            intent.putExtra(getString(R.string.curr_audio_id), rowId);
            startActivity(intent);
        });

        // See https://developer.android.com/guide/topics/ui/menus.html#CAB for details
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Регулировка меню названий и списка выделенных треков когда элементы выделяют/убирают
                if (b) {
                    mSelectedTracks.add(l);
                } else {
                    mSelectedTracks.remove(l);
                }
                String menuTitle = getResources().getString(R.string.items_selected, mSelectedTracks.size());
                actionMode.setTitle(menuTitle);
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Inflate меню для CAB
                getMenuInflater().inflate(R.menu.menu_album_cab, menu);
                // Без этого меню будет показываться в action bar вместо overflow menu
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
                return true;

            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_delete:
                        // Проверить отмечены ли все необходимые разрешения
                        if (ContextCompat.checkSelfPermission(AlbumActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            // Это необходимо, так как запрос разрешения destroys action mode
                            // для того чтобы все треки были очищены
                            mTmpSelectedTracks = new ArrayList<>(mSelectedTracks);
                            ActivityCompat.requestPermissions(AlbumActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE);
                        } else {
                            deleteSelectedTracksWithConfirmation();
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_delete_from_db:
                        deleteSelectedTracksFromDBWithConfirmation();
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_not_started:
                        for (long trackId : mSelectedTracks) {
                            DBAccessUtils.markTrackAsNotStarted(AlbumActivity.this, trackId);
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_completed:
                        for (long trackId : mSelectedTracks) {
                            DBAccessUtils.markTrackAsCompleted(AlbumActivity.this, trackId);
                        }
                        actionMode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Внесите необходимые обновления в действие при удалении CAB.
                // По умолчанию выбранные элементы не отмечены/отмечены.
                mSelectedTracks.clear();
            }
        });

        //уУстановка SwipeRefreshLayout onRefresh
        mSwipeRefreshLayout = findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> mSynchronizer.updateDBTables());

        // Установка FAB onClickListener
        mPlayPauseFAB.setOnClickListener(view -> {
            if (mPlayer == null) {
                return;
            }
            if (mPlayer.isPlaying()) {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PAUSE_AUDIO);
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(broadcastIntent);
            } else {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PLAY_AUDIO);
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(broadcastIntent);
            }
        });

        // Привязать к MediaPlayerService, если он был запущен PlayActivity
        bindToServiceIfRunning();

        // Настроить приемник статуса воспроизведения
        mPlayStatusReceiver = new PlayStatusReceiver(mPlayPauseFAB);
        mPlayStatusReceiver.setListener(this);

        // Регистрация BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));

        // Это должен быть приемник для глобальных трансляций, так как deleteIntent транслируется
        // платформой уведомлений Android
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindToServiceIfRunning();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    protected void onRestart() {
        // Воссоздать, если тема изменилась
        boolean currentDarkTheme;
        currentDarkTheme = mPrefs.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        if (mDarkTheme != currentDarkTheme) {
            recreate();
        }
        // Синхронизировать, если изменился параметр show-hidden-files
        boolean currentShowHiddenFiles;
        currentShowHiddenFiles = mPrefs.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));
        if (mShowHiddenFiles != currentShowHiddenFiles) {
            mSwipeRefreshLayout.setRefreshing(true);
            mSynchronizer.updateDBTables();
            mShowHiddenFiles = currentShowHiddenFiles;
        }
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        if (mServiceBound) {
            unbindService(serviceConnection);
        }
        unregisterReceiver(mRemoveNotificationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);

        // Остановить runnable от продолжения работы в фоновом режиме
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }

        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry._ID,
                HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_TITLE,
        };

        String sel = HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbum.getID())};
        String sortOrder = "CAST(" + HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + HearEraContract.AudioEntry.TABLE_NAME + "." + HearEraContract.AudioEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, HearEraContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Скрыть индикатор выполнения после завершения загрузки.
        ProgressBar progressBar = findViewById(R.id.progressBar_album);
        progressBar.setVisibility(View.GONE);

        // Установите текст пустого представления
        mEmptyTV.setText(R.string.no_audio_files);

        // Установить обложку альбома
        int reqSize = getResources().getDimensionPixelSize(R.dimen.album_info_height);
        BitmapUtils.setImage(mAlbumInfoCoverIV, mAlbum.getCoverPath(), reqSize);

        // Установите время информации об альбоме
        int[] times = DBAccessUtils.getAlbumTimes(this, mAlbum.getID());
        Log.e("AlbumActivity", "Update AlbumLastCompletedTime");
        if (mPlayer != null) {
            mCurrAudioLastCompletedTime = mPlayer.getCurrentAudioFile().getCompletedTime();
            mCurrUpdatedAudioId = mPlayer.getCurrentAudioFile().getID();
        }
        mAlbumLastCompletedTime = times[0];
        mAlbumDuration = times[1];
        String timeStr = Utils.getTimeString(this, times[0], times[1]);
        mAlbumInfoTimeTV.setText(timeStr);

        mAlbumInfoTitleTV.setText(mAlbum.getTitle());

        // Поменяйте местами новый курсор. Фреймворк закроет старый.
        mCursorAdapter.swapCursor(cursor);
        scrollToLastPlayed(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Это вызывается, когда последний Cursor, предоставленный onLoadFinished(), собирается закрыться.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_album, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_settings:
                // Отправить intent открыть настройки
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
        }

        return (super.onOptionsItemSelected(item));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MainActivity.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE: {
                // Если запрос отменен, массивы результатов пусты.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Разрешение не было предоставлено
                    Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
                } else {
                    mSelectedTracks = mTmpSelectedTracks;
                    deleteSelectedTracksWithConfirmation();
                }
                break;
            }
        }
    }

    /*
     * Привяжите AlbumActivity к MediaPlayerService, если служба была запущена в PlayActivity.
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("AlbumActivity", "OnServiceConnected called");
            // Мы привязались к LocalService, приводим IBinder и получаем экземпляр LocalService.
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            mServiceBound = true;

            // Выполняем действия, которые можно выполнить только после подключения сервиса
            // Настраиваем FAB-изображение воспроизведения-паузы в соответствии с текущим состоянием MediaPlayerService
            mPlayPauseFAB.setVisibility(View.VISIBLE);
            if (mPlayer.isPlaying()) {
                mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
            } else {
                mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
            }

            Log.e("AlbumActivity", "Update currAudioLastCompletedTime");
            if (mPlayer.getCurrentAudioFile().getAlbumId() == mAlbum.getID()) {
                setCompletedTimeUpdater();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e("AlbumActivity", "OnServiceDisconnected called");
        }
    };

    @Override
    public void onPlayMsgReceived() {
        mDoNotBindService = false;
    }

    @Override
    public void onSynchronizationFinished() {
        getLoaderManager().restartLoader(0, null, AlbumActivity.this);
        mSwipeRefreshLayout.setRefreshing(false);
        Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();

    }

    /*
     * Привязать к MediaPlayerService, если он был запущен PlayActivity
     */
    private void bindToServiceIfRunning() {
        Log.e("AlbumActivity", "service bound: " + mServiceBound + "do not bind service: " + mDoNotBindService);
        if (!mServiceBound && !mDoNotBindService && Utils.isMediaPlayerServiceRunning(this)) {
            Log.e("AlbumActivity", "Service is running - binding service");
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            bindService(playerIntent, serviceConnection, BIND_AUTO_CREATE);
            mServiceBound = true;
        }
    }

    /*
     * Отвязать AlbumActivity от MediaPlayerService, когда пользователь удаляет уведомление
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("AlbumActivity", "Received broadcast 'remove notification'");
            if (mServiceBound) {
                unbindService(serviceConnection);
                mServiceBound = false;
            }
            mPlayPauseFAB.setVisibility(View.GONE);
            mDoNotBindService = true;
        }
    };

    /*
     * Обновите ход воспроизведения текущего элемента ListView, а также прогресс альбома.
     * во время воспроизведения трека
     */
    private void setCompletedTimeUpdater() {
        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
                // Остановить runnable когда сервис не подключен
                if (!mServiceBound) {
                    mHandler.removeCallbacks(mRunnable);
                    return;
                }

                // Получить индекс текущего аудиофайла в виде списка
                StorageUtil storage = new StorageUtil(getApplicationContext());
                int index = storage.loadAudioIndex();

                // Получить элемент ListView для текущего аудиофайла
                View v = mListView.getChildAt(index - mListView.getFirstVisiblePosition());

                if (mPlayer != null && mPlayer.isPlaying() && mPlayer.getCurrentAudioFile().getID() == mCurrUpdatedAudioId) {
                    // Установите строку прогресса для воспроизводимого в данный момент элемента ListView
                    int completedTime = mPlayer.getCurrentPosition();
                    if (v != null) {
                        TextView durationTV = v.findViewById(R.id.audio_file_item_duration);
                        int duration = mPlayer.getCurrentAudioFile().getTime();
                        String timeStr = Utils.getTimeString(AlbumActivity.this, completedTime, duration);
                        durationTV.setText(timeStr);
                    }

                    // Установите строку прогресса для альбома
                    int currCompletedAlbumTime = mAlbumLastCompletedTime - mCurrAudioLastCompletedTime + completedTime;
                    String albumTimeStr = Utils.getTimeString(AlbumActivity.this, currCompletedAlbumTime, mAlbumDuration);
                    mAlbumInfoTimeTV.setText(albumTimeStr);
                }
                mHandler.postDelayed(this, 100);
            }
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    /*
     * Прокрутите до последней незавершенной дорожки в представлении списка
     */
    private void scrollToNotCompletedAudio(Cursor c) {
        // Прокрутите строки базы данных и проверьте незавершенные треки.
        int scrollTo = 0;
        c.moveToFirst();
        while (c.moveToNext()) {
            int duration = c.getInt(c.getColumnIndexOrThrow(HearEraContract.AudioEntry.COLUMN_TIME));
            int completed = c.getInt(c.getColumnIndexOrThrow(HearEraContract.AudioEntry.COLUMN_COMPLETED_TIME));
            if (completed < duration || duration == 0) {
                break;
            }
            scrollTo += 1;
        }
        mListView.setSelection(Math.max(scrollTo - 1, 0));
    }

    /*
     * Прокрутите до последней воспроизведенной дорожки в списке
     */
    private void scrollToLastPlayed(Cursor c) {
        // Получить текущий последний воспроизведенный ID
        long lastPlayedID = Album.getAlbumByID(this, mAlbum.getID()).getLastPlayedID();
        // Прокрутите строки курсора и проверьте ID, соответствующий последней воспроизведенной дорожке.
        int count = 0;
        int scrollTo = 0;
        c.moveToFirst();
        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndexOrThrow(HearEraContract.AudioEntry._ID));
            if (id == lastPlayedID) {
                scrollTo = count;
                break;
            }
            count += 1;
        }
        mListView.setSelection(Math.max(scrollTo, 0));
    }

    /*
     * Покажите диалоговое окно подтверждения и позвольте пользователю решить, следует ли удалить выбранный
     * треки из базы данных
     */
    private void deleteSelectedTracksFromDBWithConfirmation() {
        Long[] selectedTracks = new Long[mSelectedTracks.size()];
        final Long[] selectedTracksArr = mSelectedTracks.toArray(selectedTracks);

        // Создать диалоговое окно подтверждения
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_audio_from_db);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Пользователь нажал кнопку «Ок», поэтому удаляем треки из базы данных.
            int deletionCount = 0;
            for (long trackId : selectedTracksArr) {
                boolean deleted = DBAccessUtils.deleteTrackFromDB(AlbumActivity.this, trackId);
                if (deleted) {
                    deletionCount++;
                }
            }
            String deletedTracks = getResources().getString(R.string.tracks_deleted_from_db, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // Пользователь нажал кнопку «Отмена», поэтому закрываем диалоговое окно.
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создайте и покажите AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Показать диалоговое окно подтверждения и позволить пользователю решить, удалять ли выбранные треки
     */
    private void deleteSelectedTracksWithConfirmation() {
        Long[] selectedTracks = new Long[mSelectedTracks.size()];
        final Long[] selectedTracksArr = mSelectedTracks.toArray(selectedTracks);

        // Создать диалоговое окно подтверждения
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_audio);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Пользователь нажал кнопку «Ок», поэтому умаляем выбранные аудиофайлы
            int deletionCount = 0;
            for (long audioFileID : selectedTracksArr) {
                // Остановливаем MediaPlayerService, если текущий воспроизводимый файл находится в удаленном каталоге.
                if (mPlayer != null) {
                    long activeAudioId = mPlayer.getCurrentAudioFile().getID();
                    if (activeAudioId == audioFileID) {
                        mPlayer.stopMedia();
                        mPlayer.stopSelf();
                    }
                }

                // Удалить аудиофайл
                boolean keepDeleted = mPrefs.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));
                AudioFile audioFile =  AudioFile.getAudioFileById(AlbumActivity.this, audioFileID);
                boolean deleted = Utils.deleteTrack(this, audioFile, keepDeleted);
                if (deleted) deletionCount += 1;
            }
            mSynchronizer.updateDBTables();
            String deletedTracks = getResources().getString(R.string.tracks_deleted, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
            mSelectedTracks.clear();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            //  Пользователь нажал кнопку «Отмена», поэтому закрываем диалоговое окно.
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Создайте и покажите AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
