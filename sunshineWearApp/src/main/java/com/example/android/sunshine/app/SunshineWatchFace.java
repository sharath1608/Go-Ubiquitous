/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import app.android.example.com.sunshinewatchface.R;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService implements DataApi.DataListener{
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private Bitmap mWeatherBmp;
    private Bitmap mLowBitBmp;
    private int mHighTemp;
    private int mLowTemp;
    private JodaTimeAndroid jdt;
    private String mShortDesc;
    private int specW, specH;
    private View myLayout;
    private TextView hour, minute, second, maxTemp, minTemp, weatherDesc, dateText;
    private ImageView weatherImage;
    private final Point displaySize = new Point();
    private boolean mIsRound;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        display.getSize(displaySize);

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
                View.MeasureSpec.EXACTLY);
        specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
                View.MeasureSpec.EXACTLY);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHighTemp = intent.getIntExtra(getString(R.string.max_temp), 0);
        mLowTemp = intent.getIntExtra(getString(R.string.min_temp), 0);
        mShortDesc = intent.getStringExtra(getString(R.string.short_desc));
        byte[] imageByteArray = intent.getByteArrayExtra(getString(R.string.weather_image));
        Bitmap bmp = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
        mWeatherBmp = rescaleBitMap(bmp);
        return super.onStartCommand(intent,flags,startId);
    }

    public Bitmap rescaleBitMap(Bitmap bitmap){
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        Matrix matrix = new Matrix();
        float scaleWidth = getResources().getDimension(R.dimen.weather_image_width)/width;
        float scaleHeight = getResources().getDimension(R.dimen.weather_image_heighr)/height;
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBmp = Bitmap.createBitmap(bitmap,0,0,width,height,matrix,false);
        bitmap.recycle();
        return resizedBmp;

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mTime = new Time();
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateScreenComponents();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mIsRound = insets.isRound();
            if(!mIsRound){
                myLayout = inflater.inflate(R.layout.wear_layout_square,null);
                Log.v(getClass().getSimpleName(),"isRound layout");
            }else{
                Log.v(getClass().getSimpleName(),"isSquare layout");
                myLayout = inflater.inflate(R.layout.wear_layout_round,null);
            }

            hour         = (TextView)myLayout.findViewById(R.id.hour_text);
            minute       = (TextView)myLayout.findViewById(R.id.minute_text);
            second       = (TextView)myLayout.findViewById(R.id.second_text);
            maxTemp      = (TextView)myLayout.findViewById(R.id.max_temp);
            minTemp      = (TextView)myLayout.findViewById(R.id.min_temp);
            dateText     = (TextView)myLayout.findViewById(R.id.date_text);
            weatherDesc  = (TextView)myLayout.findViewById(R.id.weather_short_text);
            weatherImage = (ImageView) myLayout.findViewById(R.id.weather_image);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateScreenComponents();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    break;
            }
            invalidate();
        }

        public String formatDateString(){
            StringBuilder dateBuilder = new StringBuilder();
            LocalDate localDate = new LocalDate();
            DateTimeFormatter mnthFmt = DateTimeFormat.forPattern("MMM");
            DateTimeFormatter weekFmt = DateTimeFormat.forPattern("EEE");
            dateBuilder.append(weekFmt.print(localDate));
            dateBuilder.append(", ");
            dateBuilder.append(mnthFmt.print(localDate));
            dateBuilder.append(" ");
            dateBuilder.append(localDate.getDayOfMonth());
            return dateBuilder.toString();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            String degree = ""+'\u00B0';
            mTime.setToNow();
            hour.setText(String.format("%02d", mTime.hour));
            minute.setText(String.format("%02d", mTime.minute));
            second.setText(String.format("%02d", mTime.second));
            String currentDate = formatDateString();
            String highTemp = mHighTemp + degree;
            String lowTemp = mLowTemp + degree;
            maxTemp.setText(highTemp);
            minTemp.setText(lowTemp);
            weatherDesc.setText(mShortDesc);
            dateText.setText(currentDate);

            if(isInAmbientMode()) {
                second.setTextColor(Color.GRAY);
            }else{
                if(mIsRound) {
                    second.setTextAppearance(R.style.secondStyle_round);
                }else{
                    second.setTextAppearance(R.style.secondStyle_square);
                }
            }
            weatherImage.setImageBitmap(isInAmbientMode()?mLowBitBmp : mWeatherBmp);

            myLayout.measure(specW,specH);
            myLayout.layout(0,0,myLayout.getMeasuredWidth(),myLayout.getMeasuredHeight());
            canvas.drawColor(Color.BLACK);
            myLayout.draw(canvas);
        }

        private Bitmap changeToGrayScale(Bitmap bmp){
            int height = bmp.getHeight();
            int width = bmp.getWidth();

                Bitmap ambientBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Paint paint = new Paint();
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                ColorMatrixColorFilter f = new ColorMatrixColorFilter(matrix);
                paint.setColorFilter(f);
                new Canvas(ambientBmp).drawBitmap(bmp, 0, 0, paint);
                return ambientBmp;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateScreenComponents() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }else if(isInAmbientMode() || !isVisible()){
                if(mLowBitBmp==null && mWeatherBmp!=null){
                  mLowBitBmp = changeToGrayScale(mWeatherBmp);
                }
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
