package com.variksoid.hearera.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.variksoid.hearera.R;
import com.variksoid.hearera.utils.Utils;

public class AboutActivity extends AppCompatActivity {

    TextView mVersionName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mVersionName = findViewById(R.id.about_text_tv);

        // Извлечение названия приложения и версии
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (pInfo != null) {
            String versionName = pInfo.versionName;
            String aboutText = getResources().getString(R.string.about_text, getString(R.string.app_name), versionName);
            mVersionName.setText(aboutText);
        } else {
            mVersionName.setText(getString(R.string.app_name));
        }
    }
}
