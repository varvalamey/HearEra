package com.variksoid.hearera.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;

import com.variksoid.hearera.R;
import com.variksoid.hearera.models.FileSelectorItem;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Класс для навигации по файловой системе и выбора файла
 */

public class FileDialog {
    private static final String PARENT_DIR = "..";
    private final String TAG = getClass().getName();
    private FileSelectorItem[] fileList;
    private File currentPath;
    private final Context mContext;

    public interface FileSelectedListener {
        void fileSelected(File file);
    }

    public interface DirectorySelectedListener {
        void directorySelected(File directory);
    }

    private final ListenerList<FileSelectedListener> fileListenerList = new ListenerList<>();
    private final ListenerList<DirectorySelectedListener> dirListenerList = new ListenerList<>();
    private final Activity activity;
    private final boolean mSelectDirectory;
    private String fileEndsWith;
    private final HashMap<String, HashSet<String>> childDirectories = new HashMap<>();

    public FileDialog(Activity activity, File initialPath, boolean selectDirectory, String fileEndsWith, Context context) {
        this.activity = activity;
        setFileEndsWith(fileEndsWith);
        mSelectDirectory = selectDirectory;
        if (!initialPath.exists()) initialPath = Environment.getExternalStorageDirectory();
        loadFileList(initialPath);
        mContext = context;
    }

    /**
     * @return file dialog
     */
    private Dialog createFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Создать адаптер
        ListAdapter adapter = new ArrayAdapter<FileSelectorItem>(mContext, R.layout.file_selector_item, R.id.file_name, fileList) {
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                // использовать суперкласс для создания View
                View v = super.getView(position, convertView, parent);
                ImageView iconIV = v.findViewById(R.id.file_type_icon);

                // исконку в iconIV
                iconIV.setImageResource(fileList[position].getIconId());
                return v;
            }
        };

        builder.setTitle(currentPath.getPath());
        if (mSelectDirectory) {
            builder.setPositiveButton(R.string.dialog_msg_select_dir, (dialog1, which) -> {
                Log.d(TAG, currentPath.getPath());
                fireDirectorySelectedEvent(currentPath);
            });
        }
        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog1, which) -> {
            if (dialog1 != null) {
                dialog1.dismiss();
            }
        });

        builder.setAdapter(adapter, null);
        AlertDialog dialog = builder.show();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            FileSelectorItem fileChosen = fileList[position];
            File chosenFile = getChosenFile(fileChosen.toString());
            if (chosenFile.isDirectory()) {
                loadFileList(chosenFile);
                dialog.cancel();
                dialog.dismiss();
                showDialog();
            } else if (!mSelectDirectory) {
                // При щелчке по файлу инициировать событие только в том случае, если селектор файлов предназначен для
                // выбирания файла, а не каталога. В противном случае клики по файлам игнорируются.
                fireFileSelectedEvent(chosenFile);
                dialog.dismiss();
            }
        });

        return dialog;
    }

    public void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    public void removeFileListener(FileSelectedListener listener) {
        fileListenerList.remove(listener);
    }

    public void addDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.add(listener);
    }

    public void removeDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.remove(listener);
    }

    /**
     * Show file dialog
     */
    public void showDialog() {
        createFileDialog().show();
    }

    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(listener -> listener.fileSelected(file));
    }

    private void fireDirectorySelectedEvent(final File directory) {
        dirListenerList.fireEvent(listener -> listener.directorySelected(directory));
    }

    private void loadFileList(File path) {
        this.currentPath = path;
        List<FileSelectorItem> fileList = new ArrayList<>();
        if (path.exists()) {
            if (path.getParentFile() != null) {
                fileList.add(new FileSelectorItem(PARENT_DIR, FileSelectorItem.Type.BACK));
            }
            FilenameFilter filter = (dir, filename) -> {
                File sel = new File(dir, filename);
                if (!sel.canRead()) return false;
                else {
                    boolean endsWith = fileEndsWith == null || filename.toLowerCase().endsWith(fileEndsWith);
                    return endsWith || sel.isDirectory();
                }
            };
            String[] fileListTmp = path.list(filter);
            String currPathName = path.getAbsolutePath();
            // Добавьте те каталоги, которые не могут быть отображены, потому что требуются привилегии root
            if (childDirectories.containsKey(currPathName)) {
                for (String childDir : childDirectories.get(currPathName)) {
                    if ((fileListTmp == null || !Arrays.asList(fileListTmp).contains(childDir)) && new File(currPathName, childDir).exists()) {
                        fileList.add(new FileSelectorItem(childDir, FileSelectorItem.Type.DIRECTORY));
                        Log.i("FileDialog.java", "Directory " + currPathName + " is not readable. Manually adding directory " + childDir);
                    }
                }
            }
            if (fileListTmp != null) {
                for (String file: fileListTmp) {
                    FileSelectorItem.Type type = (new File(currPathName, file).isDirectory()) ? FileSelectorItem.Type.DIRECTORY : FileSelectorItem.Type.FILE;
                    fileList.add(new FileSelectorItem(file, type));
                }
            }
        }
        Collections.sort(fileList);
        this.fileList = fileList.toArray(new FileSelectorItem[]{});
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) {
            File parentFile = currentPath.getParentFile();
            // Сохраните дочерние каталоги в хэш-карте. Это необходимо, т.к. права root
            // необходимы для чтения определенных каталогов. Таким образом, все каталоги могут быть показаны
            if (!childDirectories.containsKey(parentFile.getAbsolutePath())) {
                HashSet<String> childDirs = new HashSet<>();
                childDirectories.put(parentFile.getAbsolutePath(), childDirs);
            }
            childDirectories.get(parentFile.getAbsolutePath()).add(currentPath.getName());
            return parentFile;
        } else return new File(currentPath, fileChosen);
    }

    private void setFileEndsWith(String fileEndsWith) {
        this.fileEndsWith = fileEndsWith != null ? fileEndsWith.toLowerCase() : null;
    }
}

class ListenerList<L> {
    private final List<L> listenerList = new ArrayList<>();

    public interface FireHandler<L> {
        void fireEvent(L listener);
    }

    public void add(L listener) {
        listenerList.add(listener);
    }

    void fireEvent(FireHandler<L> fireHandler) {
        List<L> copy = new ArrayList<>(listenerList);
        for (L l : copy) {
            fireHandler.fireEvent(l);
        }
    }

    void remove(L listener) {
        listenerList.remove(listener);
    }

    public List<L> getListenerList() {
        return listenerList;
    }
}
