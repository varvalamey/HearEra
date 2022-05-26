package com.variksoid.hearera.activities;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;
import com.variksoid.hearera.R;
import com.variksoid.hearera.adapters.DirectoryCursorAdapter;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.dialogs.FileDialog;
import com.variksoid.hearera.helpers.Synchronizer;
import com.variksoid.hearera.listeners.SynchronizationStateListener;
import com.variksoid.hearera.models.Directory;
import com.variksoid.hearera.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class DirectoryActivity extends AppCompatActivity  implements LoaderManager.LoaderCallbacks<Cursor>, SynchronizationStateListener {
    //firebase переменные
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference().child("files");
    ArrayList<StorageReference> firebaseFilesUrls;
    final long SIX_MEGABYTES = 1024 * 1024 * 6;







    // CursorLoader переменные
    private static final int AUDIO_LOADER = 0;
    private DirectoryCursorAdapter mCursorAdapter;

    // переменные для множественного выбора
    ArrayList<Long> mSelectedDirectories = new ArrayList<>();

    // Views
    ListView mListView;
    TextView mEmptyTV;

    // Синхронайзер
    private Synchronizer mSynchronizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        firebaseFilesUrls = new ArrayList<>();

        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directory);

        // Подготовьте CursorLoader. Либо повторно подключитесь к существующему, либо начните новый.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Инициализировать адаптер курсора
        mCursorAdapter = new DirectoryCursorAdapter(this, null);

        // Установка FloatingActionsMenu
        FloatingActionsMenu addDirectoryFAM = findViewById(R.id.add_directory_fam);
        FloatingActionButton addSubDirFAB = findViewById(R.id.add_sub_dir_fab);
        FloatingActionButton addParentDirFAB = findViewById(R.id.add_parent_dir_fab);

        // Установка overlay
        View overlay = findViewById(R.id.overlay);
        overlay.setOnClickListener(v -> addDirectoryFAM.collapse());

        // Установка FAM update listener
        addDirectoryFAM.setOnFloatingActionsMenuUpdateListener( new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener()
        {
            @Override
            public void onMenuExpanded()
            {
                overlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onMenuCollapsed()
            {
                overlay.setVisibility(View.INVISIBLE);
            }
        });

        // Установка FAB onClickListeners
        addSubDirFAB.setOnClickListener(v -> {
            addDirectory(false);
            addDirectoryFAM.collapse();
        });
        addParentDirFAB.setOnClickListener(v -> {
            addDirectory(true);
            addDirectoryFAM.collapse();
        });

        //получаем список всех файлов в firebase
        storageRef.listAll().addOnSuccessListener(new OnSuccessListener<ListResult>() {
            @Override
            public void onSuccess(ListResult listResult){
                for (StorageReference item : listResult.getItems()) {
                    //Add code to save images here
                    firebaseFilesUrls.add(item);
                }

                for (StorageReference audioRef : firebaseFilesUrls) {
                    File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    final File theAudio = new File(baseDirectory, "aud1");

                    Task task = audioRef.getFile(theAudio);
                    task.addOnSuccessListener(new OnSuccessListener() {
                        @Override
                        public void onSuccess(Object o) {
                        }
                    });

                }
            }});


        // Используйте ListView и CursorAdapter для повторного использования пространства
        mListView = findViewById(R.id.list);
        mListView.setAdapter(mCursorAdapter);

        // Установите EmptyView для ListView
        mEmptyTV = findViewById(R.id.emptyList);
        mListView.setEmptyView(mEmptyTV);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Настройте заголовок меню и список выбранных каталогов, когда элементы выбраны / не выбраны
                if (b) {
                    mSelectedDirectories.add(l);
                } else {
                    mSelectedDirectories.remove(l);
                }
                String menuTitle = getResources().getString(R.string.items_selected, mSelectedDirectories.size());
                actionMode.setTitle(menuTitle);
            }
            //
            //






            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Покажите меню для CAB
                getMenuInflater().inflate(R.menu.menu_directory_cab, menu);
                // Без этого, меню будет всегда открываться в action bar, а не в overflow menu
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
                    case R.id.menu_delete_from_db:
                        // Удалить все треки в выбранных альбомах из базы данных, если они больше не существуют в файловой системе
                        deleteSelectedDirectoriesFromDBWithConfirmation(mSelectedDirectories);
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
                mSelectedDirectories.clear();
            }
        });

        // Инициализация синхронайзера
        mSynchronizer = new Synchronizer(this);
        mSynchronizer.setListener(this);
    }

    private void addDirectory(boolean isParentDirectory) {
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, true, null, this);
        fileDialog.addDirectoryListener(directory -> {
            Directory.Type directoryType = isParentDirectory ? Directory.Type.PARENT_DIR : Directory.Type.SUB_DIR;
            Directory newDirectory = new Directory(directory.getAbsolutePath(), directoryType);
            if (allowAddDirectory(newDirectory)) {
                mSynchronizer.addDirectory(newDirectory);
            }
        });
        fileDialog.showDialog();
    }












    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, HearEraContract.DirectoryEntry.CONTENT_URI, Directory.getColumns(), null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Скрыть индикатор выполнения после завершения загрузки.
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Установите текст пустого представления
        mEmptyTV.setText(R.string.no_directories);

        // Поменяйте местами новый курсор. Фреймворк закроет старый.
        mCursorAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Это вызывается, когда последний Cursor, предоставленный onLoadFinished(), собирается закрыться.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onSynchronizationFinished() {
        getLoaderManager().restartLoader(0, null, DirectoryActivity.this);
        Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedDirectoriesFromDBWithConfirmation(ArrayList<Long> selectedDirectories) {
        Long[] selectedDirectoriesTmpArr = new Long[selectedDirectories.size()];
        final Long[] selectedDirectoriesArr = selectedDirectories.toArray(selectedDirectoriesTmpArr);

        // Создать диалоговое окно подтверждения
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_directory_from_db);
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Пользователь нажал кнопку «Ок», поэтому удалить директории из базы данных
            int deletionCount = 0;
            for (long directoryId : selectedDirectoriesArr) {
                Uri uri = ContentUris.withAppendedId(HearEraContract.DirectoryEntry.CONTENT_URI, directoryId);
                getContentResolver().delete(uri, null, null);
                deletionCount++;
            }
            String deletedTracks = getResources().getString(R.string.directories_deleted_from_db, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
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

    private boolean allowAddDirectory(Directory directory) {
        File newDir = new File(directory.getPath());
        ArrayList<Directory> directories = Directory.getDirectories(this);
        for (int i=0; i<directories.size();i++) {
            File dir = new File(directories.get(i).getPath());
            if (dir.getAbsolutePath().equals(newDir.getAbsolutePath()) && directory.getType() == directories.get(i).getType()) {
                String directoryExists = getResources().getString(R.string.directory_exists, dir.getAbsolutePath());
                Toast.makeText(getApplicationContext(), directoryExists, Toast.LENGTH_LONG).show();
                return false;
            }

        }
        return true;
    }
}
