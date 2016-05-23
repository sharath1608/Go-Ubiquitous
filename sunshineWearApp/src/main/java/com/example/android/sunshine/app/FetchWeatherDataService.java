package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import app.android.example.com.sunshinewatchface.R;

public class FetchWeatherDataService extends WearableListenerService implements GoogleApiClient.OnConnectionFailedListener,GoogleApiClient.ConnectionCallbacks
{

    private Asset mWeatherAsset;
    private GoogleApiClient mGoogleApiClient;
    private Intent weatherIntent;
    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(FetchWeatherDataService.this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        weatherIntent = new Intent(this,SunshineWatchFace.class);
        mGoogleApiClient.connect();
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                String path = event.getDataItem().getUri().getPath();
                if (path.equals(getString(R.string.weatherID_path))) {
                    int weatherId = dataMap.getInt(getString(R.string.weather_id));
                    int maxTemp = dataMap.getInt(getString(R.string.max_temp));
                    int minTemp = dataMap.getInt(getString(R.string.min_temp));
                    String weatherDesc = dataMap.getString(getString(R.string.short_desc));
                    mWeatherAsset = dataMap.getAsset(getString(R.string.weather_asset));
                    Bitmap bmap = loadBitmapFromAsset(mWeatherAsset);
                    byte[] byteArray = transformToArray(bmap);
                    weatherIntent.putExtra(getString(R.string.max_temp),maxTemp);
                    weatherIntent.putExtra(getString(R.string.min_temp),minTemp);
                    weatherIntent.putExtra(getString(R.string.short_desc),weatherDesc);
                    weatherIntent.putExtra(getString(R.string.weather_image),byteArray);
                    weatherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
            }
        }
        mGoogleApiClient.disconnect();
        startService(weatherIntent);
    }

    public byte[] transformToArray(Bitmap bmp){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        return outputStream.toByteArray();
    }

    public Bitmap loadBitmapFromAsset(Asset asset){
        if(asset == null){
            throw new IllegalArgumentException("weather asset cannot be null");
        }
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient,asset).await().getInputStream();
        mGoogleApiClient.disconnect();
        if(assetInputStream == null){
            Log.w(getClass().getSimpleName(),"Requested an unknown asset"+asset.uri);
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

}