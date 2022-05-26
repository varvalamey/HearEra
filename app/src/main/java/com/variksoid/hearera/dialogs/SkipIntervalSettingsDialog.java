package com.variksoid.hearera.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.variksoid.hearera.R;
import com.variksoid.hearera.helpers.SkipIntervalPreference;
import com.variksoid.hearera.utils.SkipIntervalUtils;

public class SkipIntervalSettingsDialog extends PreferenceDialogFragmentCompat {

    int mSkipIntervalValue;
    TextView mSkipIntervalTV;
    SeekBar mSkipIntervalSB;
    ImageView mSeekbarMaxIV;
    ImageView mDecreaseIntervalIV;
    ImageView mIncreaseIntervalIV;

    public static SkipIntervalSettingsDialog newInstance(String key) {
        final SkipIntervalSettingsDialog fragment = new SkipIntervalSettingsDialog();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSkipIntervalTV = view.findViewById(R.id.skip_interval_tv);
        mSkipIntervalSB = view.findViewById(R.id.skip_interval_sb);
        mSeekbarMaxIV = view.findViewById(R.id.seekbar_max_iv);
        mDecreaseIntervalIV = view.findViewById(R.id.decrease_interval_iv);
        mIncreaseIntervalIV = view.findViewById(R.id.increase_interval_iv);

        // Установите значок, указывающий максимальное значение SeekBar, на значок «следующий», если
        // соответствующая кнопка — это кнопка «вперед» (значок по умолчанию — «предыдущий»)
        if (((SkipIntervalPreference) getPreference()).isForward()) {
            mSeekbarMaxIV.setImageResource(R.drawable.ic_next);
        }

        // Получите значение skipInterval из соответствующего параметра Preference.
        mSkipIntervalValue = 0;
        DialogPreference preference = getPreference();
        if (preference instanceof SkipIntervalPreference) {
            mSkipIntervalValue = ((SkipIntervalPreference) preference).getSkipInterval();
        }

        // Установите начальные значения SeekBar и TextView
        mSkipIntervalSB.setProgress(SkipIntervalUtils.getProgressFromSkipInterval(mSkipIntervalValue));
        mSkipIntervalTV.setText(getSkipIntervalString(mSkipIntervalValue));

        mSkipIntervalSB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int skipInterval = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
                mSkipIntervalTV.setText(getSkipIntervalString(skipInterval));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(seekBar.getProgress());
            }
        });

        mDecreaseIntervalIV.setOnClickListener(view1 -> {
            int progress = mSkipIntervalSB.getProgress();
            progress = Math.max(0, progress - 1);
            mSkipIntervalSB.setProgress(progress);
            mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
        });

        mIncreaseIntervalIV.setOnClickListener(view1 -> {
            int progress = mSkipIntervalSB.getProgress();
            progress = Math.min(mSkipIntervalSB.getMax(), progress + 1);
            mSkipIntervalSB.setProgress(progress);
            mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
        });
    }

    String getSkipIntervalString(int skipInterval) {
        if (skipInterval == SkipIntervalUtils.getSkipIntervalFromProgress(mSkipIntervalSB.getMax())) {
            if (((SkipIntervalPreference) getPreference()).isForward()) {
                return getResources().getString(R.string.settings_skip_interval_next);
            } else {
                return getResources().getString(R.string.settings_skip_interval_previous);
            }
        } else {
            return getResources().getString(R.string.settings_skip_interval, skipInterval);
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Получите соответствующее предпочтение и сохраните значение
            DialogPreference preference = getPreference();
            if (preference instanceof SkipIntervalPreference) {
                SkipIntervalPreference skipIntervalPreference = ((SkipIntervalPreference) preference);
                // Это позволяет клиенту игнорировать значение пользователя.
                if (skipIntervalPreference.callChangeListener(mSkipIntervalValue)) {
                    // Сохраняем значение
                    skipIntervalPreference.setSkipInterval(mSkipIntervalValue);
                }
            }
        }
    }
}
