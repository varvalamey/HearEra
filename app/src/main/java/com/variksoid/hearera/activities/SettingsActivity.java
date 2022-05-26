package com.variksoid.hearera.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.variksoid.hearera.R;
import com.variksoid.hearera.dialogs.SkipIntervalSettingsDialog;
import com.variksoid.hearera.helpers.SkipIntervalPreference;
import com.variksoid.hearera.models.Bookmark;
import com.variksoid.hearera.utils.SkipIntervalUtils;
import com.variksoid.hearera.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (mPrefListener == null) {
            mPrefListener = (prefs, key) -> {
                if (key.equals(getString(R.string.settings_dark_key))) {
                    recreate();
                }
            };
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
    }

    public static class AnchorPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            initSummary(getPreferenceScreen());
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            Preference pref = findPreference(s);

            updatePrefSummary(pref);

            if (s.equals(getString(R.string.settings_add_last_play_position_bookmark_key))) {
                boolean isChecked = ((SwitchPreference) pref).isChecked();
                if (!isChecked) {
                    Bookmark.deleteBookmarksWithTitle(getActivity(), getString(R.string.bookmark_last_play_position));
                    Bookmark.deleteBookmarksWithTitle(getActivity(), getString(R.string.bookmark_second_to_last_play_position));
                }
            }
        }

        private void initSummary(Preference p) {
            if (p instanceof PreferenceGroup) {
                PreferenceGroup pGrp = (PreferenceGroup) p;
                for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                    initSummary(pGrp.getPreference(i));
                }
            } else {
                updatePrefSummary(p);
            }
        }

        private void updatePrefSummary(Preference p) {
            if (p instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) p;
                p.setSummary(editTextPref.getText());
            } else if (p instanceof ListPreference) {
                p.setSummary(((ListPreference)p).getEntry());
            } else if (p instanceof SkipIntervalPreference) {;
                int skipInterval = ((SkipIntervalPreference)p).getSkipInterval();
                String skipIntervalStr;
                if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
                    boolean isForwardButton = ((SkipIntervalPreference)p).isForward();
                    skipIntervalStr = (isForwardButton) ? getResources().getString(R.string.settings_skip_interval_next) : getResources().getString(R.string.settings_skip_interval_previous);
                } else {
                    skipIntervalStr = String.valueOf(skipInterval);
                }
                p.setSummary(skipIntervalStr);
            }
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            // Проверьте, является ли предпочтение SkipIntervalPreference
            DialogFragment dialogFragment = null;
            if (preference instanceof SkipIntervalPreference) {
                // Создайте новый экземпляр SkipButtonSettingDialog с ключом связанного
                // Preference
                dialogFragment = SkipIntervalSettingsDialog.newInstance(preference.getKey());
            }

            // Если это была одна из наших пользовательских настроек, покажите ее диалоговое окно.
            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(this.getParentFragmentManager(), "com.variksoid.hearera.helpers.SkipIntervalPreference");
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
