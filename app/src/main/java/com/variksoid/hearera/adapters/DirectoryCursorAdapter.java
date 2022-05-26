package com.variksoid.hearera.adapters;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.models.Directory;


import java.io.File;

public class DirectoryCursorAdapter extends CursorAdapter {

    public DirectoryCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
    }
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.directory_item, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        //Получите название текущего альбома и установите этот текст в titleTV
        TextView titleTV = view.findViewById(R.id.directory_title_tv);

        String path = cursor.getString(cursor.getColumnIndexOrThrow(HearEraContract.DirectoryEntry.COLUMN_PATH));
        File dir = new File(path);
        titleTV.setText(dir.getName());

        TextView subtitleTV = view.findViewById(R.id.directory_subtitle_tv);
        subtitleTV.setText(dir.getAbsolutePath());

        Directory.Type type = Directory.Type.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(HearEraContract.DirectoryEntry.COLUMN_TYPE)));
        ImageView icon = view.findViewById(R.id.directory_icon);
        if (type == Directory.Type.SUB_DIR) {
            icon.setImageResource(R.drawable.ic_directory_grey);
        } else {
            icon.setImageResource(R.drawable.ic_parent_directory_grey);
        }

        // Показать удаляемое изображение, если файл больше не существует
        ImageView deletableIV = view.findViewById(R.id.directory_item_deletable_img);
        if (!dir.exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }
}
