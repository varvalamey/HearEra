package com.variksoid.hearera.adapters;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.variksoid.hearera.R;
import com.variksoid.hearera.data.HearEraContract;
import com.variksoid.hearera.utils.Utils;


/**
 * CursorAdapterдля диалогового окна «Закладка» в PlayActivity
 */

public class BookmarkCursorAdapter extends CursorAdapter {
    private final long mTotalMillis;

    public BookmarkCursorAdapter(Context context, Cursor c, long totalMillis) {
        super(context, c, 0);
        mTotalMillis = totalMillis;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.bookmark_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Получите заголовок текущей закладки и установите этот текст в titleTV
        TextView titleTV = view.findViewById(R.id.bookmark_title_tv);
        String title = cursor.getString(cursor.getColumnIndexOrThrow(HearEraContract.BookmarkEntry.COLUMN_TITLE));
        titleTV.setText(title);
        // Получить позицию текущей закладки
        TextView positionTV = view.findViewById(R.id.bookmark_position_tv);
        long position = cursor.getLong(cursor.getColumnIndexOrThrow(HearEraContract.BookmarkEntry.COLUMN_POSITION));
        String positionString = Utils.formatTime(position, mTotalMillis);
        positionTV.setText(positionString);
    }
}
