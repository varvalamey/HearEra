package com.variksoid.hearera.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.variksoid.hearera.R;
import com.variksoid.hearera.listeners.SleepTimerStatusListener;
import com.variksoid.hearera.services.MediaPlayerService;
import com.variksoid.hearera.utils.Utils;

public class SleepTimer {

    private CountDownTimer mSleepTimer;

    private MediaPlayerService mPlayer;
    private SleepTimerStatusListener mListener;
    private TextView mSleepCountDownTV;
    private long mCurrentMillisLeft;
    private int mSecSleepTime;

    // Shake stuff
    private boolean mShakeDetectionEnabled;
    private final SensorManager mSensorMng;
    private final ShakeDetector mShakeDetector;
    private float mShakeForceRequired = 3f;

    // Preferences
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    public SleepTimer(TextView sleepCountDownTV, MediaPlayerService mediaPlayer, SensorManager sensorMng,
                      Context context) {
        this(sleepCountDownTV, sensorMng, context);
        mPlayer = mediaPlayer;
    }

    public SleepTimer(TextView sleepCountDownTV, SensorManager sensorMng,
                      Context context) {
        mSleepCountDownTV = sleepCountDownTV;
        mSensorMng = sensorMng;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
        mPlayer = null;
        mShakeDetector = new ShakeDetector(mShakeForceRequired) {
            @Override
            public void shakeDetected() {
                boolean vibrateOnShakeReset = mSharedPreferences.getBoolean(context.getString(R.string.settings_vibrate_shake_reset_key), Boolean.getBoolean(context.getString(R.string.settings_vibrate_shake_reset_default)));
                if (vibrateOnShakeReset) {
                    Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (v != null) {
                            v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                    } else {
                        if (v != null) {
                            //deprecated in API 26
                            v.vibrate(100);
                        }
                    }
                }
                restartTimer();
                startTimer(false);
            }
        };
    }

    public void createTimer(final int secSleepTime, boolean shakeDetectionEnabled, float shakeForceRequiredPercent) {
        // Позвольте пользователю отключить таймер, введя 0 или ничего
        if (secSleepTime == 0) {
            disableTimer();
            return;
        }

        final float mShakeForceMax = 20f;
        final float mShakeForceMin = 0.5f;

        mShakeDetectionEnabled = shakeDetectionEnabled;

        if (shakeForceRequiredPercent <= 1f && shakeForceRequiredPercent >= 0f) {
            mShakeForceRequired = ((mShakeForceMax - mShakeForceMin) * shakeForceRequiredPercent) + mShakeForceMin;
            mShakeDetector.setShakeTresh(mShakeForceRequired);
        }

        createTimer(secSleepTime);
    }

    private void createTimer(final int secSleepTime) {
        mSecSleepTime = secSleepTime;
        long millis = secSleepTime * 1000;
        mSleepCountDownTV.setVisibility(View.VISIBLE);
        mCurrentMillisLeft = millis;
        String timeString = Utils.formatTime(millis, millis);
        mSleepCountDownTV.setText(timeString);

        if (mSleepTimer != null)
            mSleepTimer.cancel();

        mSleepTimer = new CountDownTimer(millis, 1000) {

            @Override
            public void onTick(long l) {
                mCurrentMillisLeft = l;

                // Обновить текст
                String timeString = Utils.formatTime(l, secSleepTime * 1000);
                mSleepCountDownTV.setText(timeString);

                // Fade-out
                int fadeoutTime = Integer.parseInt(mSharedPreferences.getString(mContext.getString(R.string.settings_sleep_fadeout_key), mContext.getString(R.string.settings_sleep_fadeout_default)));
                // время затухания не может превышать общую продолжительность таймера сна.
                // В противном случае время затухания будет резко уменьшено после запуска таймера сна.
                fadeoutTime = Math.min(fadeoutTime, secSleepTime);
                boolean continueUntilEndOfTrack = mSharedPreferences.getBoolean(mContext.getString(R.string.settings_continue_until_end_key), Boolean.getBoolean(mContext.getString(R.string.settings_continue_until_end_default)));
                if (!continueUntilEndOfTrack && (l / 1000) < fadeoutTime) {
                    if (mPlayer != null) mPlayer.decreaseVolume((int) (fadeoutTime - (l / 1000)), fadeoutTime);
                }
            }

            @Override
            public void onFinish() {
                if (mListener != null) mListener.onSleepTimerFinished();
                boolean continueUntilEndOfTrack = mSharedPreferences.getBoolean(mContext.getString(R.string.settings_continue_until_end_key), Boolean.getBoolean(mContext.getString(R.string.settings_continue_until_end_default)));
                if (!continueUntilEndOfTrack) {
                    disableTimer();
                }
            }
        };
    }

    public void setPlayer(MediaPlayerService player) { mPlayer = player; }

    public void setListener(SleepTimerStatusListener listener) { mListener = listener; }

    public void setNewSleepCountDownTV(TextView countDownTV) {
        boolean visible = false;
        if (mSleepCountDownTV.getVisibility() == View.VISIBLE) {
            visible = true;
        }
        mSleepCountDownTV = countDownTV;

        if (visible) {
            String timeString = Utils.formatTime(mCurrentMillisLeft, mSecSleepTime * 1000);
            mSleepCountDownTV.setText(timeString);
            mSleepCountDownTV.setVisibility(View.VISIBLE);
        }
    }

    private void startShakeDetection() {
        if (mSensorMng != null) {
            Sensor sensor = mSensorMng.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorMng.registerListener(
                    mShakeDetector,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void disableTimer() {
        mCurrentMillisLeft = 0;

        if (mSleepTimer != null) {
            mSleepTimer.cancel();
            mSleepCountDownTV.setVisibility(View.GONE);

            // Нам нужно, чтобы приложение не сбрасывало громкость на более медленных системах перед паузой.
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                if (mPlayer != null) mPlayer.setVolume(1.0f);
            }, 500);
        }

        if (mSensorMng != null) {
            mSensorMng.unregisterListener(mShakeDetector);
        }
    }

    private void restartTimer() {
        if (mCurrentMillisLeft > 0) {
            mSleepTimer.cancel();
            if (mPlayer != null) mPlayer.setVolume(1.0f);
            createTimer(mSecSleepTime);
        }

        if (mSensorMng != null) {
            mSensorMng.unregisterListener(mShakeDetector);
        }
    }

    public void startTimer(boolean onlyIfPlaying) {
        if (mCurrentMillisLeft <= 0)
            return;

        if (onlyIfPlaying) {
            if (mPlayer != null && mPlayer.isPlaying()) {
                mSleepTimer.start();
                if (mShakeDetectionEnabled)
                    startShakeDetection();
            }
        } else {
            mSleepTimer.start();
            if (mShakeDetectionEnabled)
                startShakeDetection();
        }
    }
}

class ShakeDetector implements SensorEventListener {
    private final float[] mGravity = {0, 0, 0};
    private final float[] mLinearAcc = {0, 0, 0};
    private long mLastMeasurementT = 0;
    private long mLastShakeDetected = 0;
    private float mShakeTresh;
    final private long mStartTime;

    ShakeDetector(float forceRequired) {
        mShakeTresh = forceRequired;
        mStartTime = System.currentTimeMillis();
    }

    void setShakeTresh(float newVal) {
        mShakeTresh = newVal;
    }

    // Обратный вызов при обнаружении тряски
    void shakeDetected() {
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // From https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-accel
        final int sampleMsec = 100;
        // После обнаружения сотрясения не реагировать на другое сотрясение в течение определенного времени.
        // В противном случае одно и то же будет обнаружено несколько раз.
        final int sampleMsecShakeDetected = 1000;
        final int settleTime = 2000; //let the filter settle


        if (System.currentTimeMillis() - mLastMeasurementT < sampleMsec) {
            return;
        }
        mLastMeasurementT = System.currentTimeMillis();

        final float alpha = 0.8f;

        // Изолируйте силу mGravity с помощью фильтра нижних частот.
        mGravity[0] = alpha * mGravity[0] + (1 - alpha) * sensorEvent.values[0];
        mGravity[1] = alpha * mGravity[1] + (1 - alpha) * sensorEvent.values[1];
        mGravity[2] = alpha * mGravity[2] + (1 - alpha) * sensorEvent.values[2];

        //Удалите вклад mGravity с помощью фильтра верхних частот.
        mLinearAcc[0] = sensorEvent.values[0] - mGravity[0];
        mLinearAcc[1] = sensorEvent.values[1] - mGravity[1];
        mLinearAcc[2] = sensorEvent.values[2] - mGravity[2];

        float totalAcc = mLinearAcc[0] + mLinearAcc[1] + mLinearAcc[2];
        if (totalAcc > mShakeTresh && System.currentTimeMillis() - mStartTime > settleTime &&
                System.currentTimeMillis() - mLastShakeDetected > sampleMsecShakeDetected) {
            mLastShakeDetected = System.currentTimeMillis();
            shakeDetected();
            Log.d("SHAKE", "Shake with force of: " + totalAcc);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}