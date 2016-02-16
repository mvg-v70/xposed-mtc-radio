package com.mvgv70.xposed_mtc_music;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class ScreenClock implements IXposedHookLoadPackage 
{
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
  private final static String SCREENCLOCK_SECTION = "screenclock";
  private static IniFile props = new IniFile();
  private static Activity screenClockActivity;
  // names
  private static String title_name;
  private static String album_name;
  private static String artist_name;
  private static String cover_name;
  private static String speed_name;
  // view
  private static TextView titleView = null;
  private static TextView albumView = null;
  private static TextView artistView = null;
  private static ImageView coverView = null;
  private static TextView speedView = null;
  private final static String TAG = "xposed-mtc-music";

  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // MainActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"ScreenClock:onCreate");
        screenClockActivity = (Activity)param.thisObject;
        // чтение настроек
        readSettings();
        // создание broadcast receiver
        createReceivers();
      }
    };
    
    // MainActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"ScreenClock:onDestroy");
        // отключаем receiver
        screenClockActivity.unregisterReceiver(tagsReceiver);
        // скорость
        if (speedView != null)
        {
          try
          {
            LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null)
            {
              // определение скорости
              locationManager.removeUpdates(locationListener);
            }
          }
          catch (Exception e) { }
        } 
      }
    };
	    	    
    // start hooks
    if (!lpparam.packageName.equals("com.microntek.screenclock")) return;
    Log.d(TAG,"com.microntek.screenclock");
    XposedHelpers.findAndHookMethod("com.microntek.screenclock.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.screenclock.MainActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.microntek.screenclock hook OK");
  }
  
  // чтение настроек из mtc-music.ini
  private void readSettings()
  {
    try
    {
      Log.d(TAG,"read settings from "+INI_FILE_NAME);
      props.loadFromFile(INI_FILE_NAME);
      // имена элементов
      title_name = props.getValue(SCREENCLOCK_SECTION, "title", "");
      Log.d(TAG,"title_name="+title_name);
      album_name = props.getValue(SCREENCLOCK_SECTION, "album", "");
      Log.d(TAG,"album_name="+album_name);
      artist_name = props.getValue(SCREENCLOCK_SECTION, "artist", "");
      Log.d(TAG,"artist_name="+artist_name);
      cover_name = props.getValue(SCREENCLOCK_SECTION, "cover", "");
      Log.d(TAG,"cover_name="+cover_name);
      speed_name = props.getValue(SCREENCLOCK_SECTION, "speed", "");
      Log.d(TAG,"speed_name="+speed_name);
      // параметры
    } catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    // id
    int title_id = 0;
    int album_id = 0;
    int artist_id = 0;
    int cover_id = 0;
    int speed_id = 0;
    if (!title_name.isEmpty())
      title_id = screenClockActivity.getResources().getIdentifier(title_name,"id", screenClockActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = screenClockActivity.getResources().getIdentifier(album_name,"id", screenClockActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = screenClockActivity.getResources().getIdentifier(artist_name,"id", screenClockActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = screenClockActivity.getResources().getIdentifier(cover_name,"id", screenClockActivity.getPackageName());
    if (!speed_name.isEmpty())
      speed_id = screenClockActivity.getResources().getIdentifier(speed_name,"id", screenClockActivity.getPackageName());
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"speed_id="+speed_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    speedView = null;
    if (title_id > 0) titleView = (TextView)screenClockActivity.findViewById(title_id);
    if (album_id > 0) albumView = (TextView)screenClockActivity.findViewById(album_id);
    if (artist_id > 0) artistView = (TextView)screenClockActivity.findViewById(artist_id);
    if (cover_id > 0) coverView = (ImageView)screenClockActivity.findViewById(cover_id);
    if (speed_id > 0) speedView = (TextView)screenClockActivity.findViewById(speed_id);
  }
  
  // создание broadcast receiver
  private void createReceivers()
  {
    IntentFilter pi = new IntentFilter();
    pi.addAction("com.android.music.playstatechanged");
    screenClockActivity.registerReceiver(tagsReceiver, pi);
    /*
    IntentFilter ti1 = new IntentFilter();
    ti1.addAction("com.microntek.VOLUME_CHANGED");
    screenClockActivity.registerReceiver(testReceiver, ti1);
    //
    IntentFilter ti2 = new IntentFilter();
    ti2.addAction("com.mvgv70.VOLUME_CHANGED");
    screenClockActivity.registerReceiver(testReceiver, ti2);
    */
    Log.d(TAG,"tagsReceiver created");
    // послать сообщение плееру о чтении тегов
    Intent intent = new Intent("com.android.music.querystate");
    screenClockActivity.sendBroadcast(intent);
    Log.d(TAG,"com.android.music.querystate sent");
    if (speedView != null)
    {
      try
      {
        LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
          // определение скорости
          locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
          Log.d(TAG,"speed listener created");
        }
      }
      catch (Exception e)
      {
        // нет прав
        Log.e(TAG,"LocationManager: "+e.getMessage());
      }
    }
  }
  
  // com.android.music.playstatechanged
  private BroadcastReceiver tagsReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"ScreenClock: tags receiver");
      // mp3 tags
      String title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE);
      String artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST);
      String album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM);
      Bitmap cover = (Bitmap)intent.getParcelableExtra("cover");
      // show tags
      Log.d(TAG,"title="+title);
      Log.d(TAG,"artist="+artist);
      Log.d(TAG,"album="+album);
      // установим теги
      if (titleView != null) titleView.setText(title); 
      if (artistView != null) artistView.setText(artist);
      if (albumView != null) albumView.setText(album);
      if (coverView != null) coverView.setImageBitmap(cover);
    }
  };
  
  private BroadcastReceiver testReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"ScreenClock: test receiver");
      String action = intent.getAction();
      Log.d(TAG,action);
    }
  };
  
  // изменение скорости
  private LocationListener locationListener = new LocationListener() 
  {
    public void onLocationChanged(Location location)
    {
      if (!location.hasSpeed()) return;
      int speed = (int)(location.getSpeed()*3.6);
      speedView.setText(Integer.valueOf(speed).toString());
    }
      
    public void onProviderDisabled(String provider) {}
      
    public void onProviderEnabled(String provider) {}
      
    public void onStatusChanged(String provider, int status, Bundle extras) {}
  };
  
}
