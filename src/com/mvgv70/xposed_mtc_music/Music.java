package com.mvgv70.xposed_mtc_music;

import java.io.File;
import java.util.ArrayList;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Music implements IXposedHookLoadPackage 
{
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
  private final static String MAIN_SECTION = "main";
  private final static String MUSIC_SECTION = "music";
  private static IniFile props = new IniFile();
  //
  private static Activity musicActivity;
  private static BroadcastReceiver usbReceiver = null;
  private static boolean mPlaying = false;
  private static Handler handler;
  // mp3-tags
  private static String currentFileName;
  private static String album = "";
  private static String title = "";
  private static String artist = "";
  private static long album_id = -1;
  private static Bitmap cover = null;
  // ��������� � mtc-music.ini
  private static String back_press_name;
  private static int back_press_time;
  private static Boolean control_keys;
  private static String album_cover_name = "";
  private static String music_list_name;
  private static String title_name = "";
  private static String album_name = "";
  private static String artist_name = "";
  private static String cover_name = "";
  private static String visualizer_name = "";
  // ��������
  private static View visualizerView = null;
  // ����
  private static TextView titleView = null;
  private static TextView artistView = null;
  private static TextView albumView = null;
  private static ImageView coverView = null;
  private final static String TAG = "xposed-mtc-music";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // MusicActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        musicActivity = (Activity)param.thisObject;
        handler = (Handler)XposedHelpers.getObjectField(musicActivity, "handler");
        // �������� ������ ������
        try 
        {
     	  Context context = musicActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
        } catch (NameNotFoundException e) {}
        // ������ ��������
        readSettings();
        // ����������� ����������� ������ � �����-������
        createReceivers();
        if (back_press_time > 0)
          // ������� ���������� ��������
          createBackPressListener();
      }
    };
    
    // MusicActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onDestroy");
    	if (usbReceiver != null) musicActivity.unregisterReceiver(mediaReceiver);
        if (control_keys) 
        {
          musicActivity.unregisterReceiver(mediaButtonReceiver);
          musicActivity.unregisterReceiver(commandReceiver);
        }
        musicActivity.unregisterReceiver(tagsQueryReceiver);
        // ������ ������ ���������� � ������������� �����
        Intent intent = new Intent("com.android.music.playstatechanged");
        musicActivity.sendBroadcast(intent);
        musicActivity = null;
      }
    };
    
    // MusicActivity.onState(int)
    XC_MethodHook onState = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onState");
        musicActivity = (Activity)param.thisObject;
        int playState = (int)param.args[0];
        mPlaying = (playState == 1);
        Log.d(TAG,"mPlaying="+mPlaying);
        sendNotifyIntent(musicActivity);
      }
    };
    
    // MusicActivity.updataMp3info()
    XC_MethodHook updataMp3info = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
      	String fileName = "";
      	Log.d(TAG,"updataMp3info");
      	int currentPlayIndex = XposedHelpers.getIntField(musicActivity, "currentPlayIndex");
      	if (currentPlayIndex >= 0)
      	{
          @SuppressWarnings("unchecked")
          ArrayList<String> list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          if (list.size() > 0)
      	    fileName = list.get(currentPlayIndex);
      	}
      	Log.d(TAG,"fileName="+fileName);
      	currentFileName = fileName;
      	// ���������� ������ mp3-�����
      	handler.postDelayed(mp3Info, 100);
      }
    };
    
    // MusicActivity.onBackPressed()
    XC_MethodHook onBackPressed = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onBackPressed");
    	if ((visualizerView != null) && (coverView != null))
    	  if (cover != null)
    		// ������ visualizer ���� ����� ����� ����������� �� ������ ������
    	    visualizerView.setVisibility(View.INVISIBLE);
      }
    };
      
    // start hooks
    if (!lpparam.packageName.equals("com.microntek.music")) return;
    Log.d(TAG,"com.microntek.music");
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onState", int.class, onState);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "updataMp3info", updataMp3info);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onBackPressed", onBackPressed);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onDestroy", onDestroy);
    // ro.product.customer
    Log.d(TAG,"com.microntek.music hook OK");
  }

  // ������ ���������� �������� �� mtc-music.ini
  private void readSettings()
  {
    try
    {
      Log.d(TAG,"read settings from "+INI_FILE_NAME);
      props.loadFromFile(INI_FILE_NAME);
      // ��� ���������� music_list
      music_list_name = props.getValue(MAIN_SECTION, "music_list", "music_list");
      Log.d(TAG,"music_list="+music_list_name);
      // control_keys
      control_keys = props.getBoolValue(MAIN_SECTION, "control_keys", true);
      Log.d(TAG,"control_keys="+control_keys);
      // backpress
      back_press_name = props.getValue(MAIN_SECTION, "backpress.name");
      back_press_time = props.getIntValue(MAIN_SECTION, "backpress.time",20);
      Log.d(TAG,"back_press_name="+back_press_name+", back_press_time="+back_press_time);
      // visualizer_name
      visualizer_name = props.getValue(MAIN_SECTION, "visualizer");
      Log.d(TAG,"visualizer_name="+visualizer_name);
      // album_cover_name
      album_cover_name = props.getValue(MAIN_SECTION, "album_cover");
      Log.d(TAG,"album_cover_name="+album_cover_name);
      // ����
      title_name = props.getValue(MUSIC_SECTION, "title", "");
      Log.d(TAG,"title_name="+title_name);
      album_name = props.getValue(MUSIC_SECTION, "album", "");
      Log.d(TAG,"album_name="+album_name);
      artist_name = props.getValue(MUSIC_SECTION, "artist", "");
      Log.d(TAG,"artist_name="+artist_name);
      cover_name = props.getValue(MUSIC_SECTION, "cover", "");
      Log.d(TAG,"cover_name="+cover_name);
    } catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    // id
    int title_id = 0;
    int album_id = 0;
    int artist_id = 0;
    int cover_id = 0;
    int visualizer_id = 0;
    if (!title_name.isEmpty())
      title_id = musicActivity.getResources().getIdentifier(title_name,"id", musicActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = musicActivity.getResources().getIdentifier(album_name,"id", musicActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = musicActivity.getResources().getIdentifier(artist_name,"id", musicActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = musicActivity.getResources().getIdentifier(cover_name,"id", musicActivity.getPackageName());
    if (!visualizer_name.isEmpty())
      visualizer_id = musicActivity.getResources().getIdentifier(visualizer_name,"id", musicActivity.getPackageName());
    //
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"visualizer_id="+visualizer_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    visualizerView = null;
    if (title_id > 0) titleView = (TextView)musicActivity.findViewById(title_id);
    if (album_id > 0) albumView = (TextView)musicActivity.findViewById(album_id);
    if (artist_id > 0) artistView = (TextView)musicActivity.findViewById(artist_id);
    if (cover_id > 0) coverView = (ImageView)musicActivity.findViewById(cover_id);
    if (visualizer_id > 0) visualizerView = (View)musicActivity.findViewById(visualizer_id);
  }  

  // �������� ����������� ������� �������� �� 20 ���.
  private void createBackPressListener()
  {
    if (back_press_name.isEmpty()) return;
    int back_press_id = musicActivity.getResources().getIdentifier(back_press_name,"id", musicActivity.getPackageName());
    Log.d(TAG,"back_press_id="+back_press_id);
    if (back_press_id == 0) return;
    View back_press_view;
    try
    {
      back_press_view = musicActivity.findViewById(back_press_id);
      back_press_view.setClickable(true);
      back_press_view.setOnClickListener(backPressClick);
    }
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  // ������� �� ��������� ����� �����
  public View.OnClickListener backPressClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"back press");
      Object mServer = XposedHelpers.getObjectField(musicActivity,"mServer");
      Log.d(TAG,"mPlayer");
      MediaPlayer mPlayer = (MediaPlayer)XposedHelpers.getObjectField(mServer,"mPlayer");
      if ((mPlayer.isPlaying()) && (back_press_time > 0))
      {
        Log.d(TAG,"playing");
        // � ������ ������������
        int position = XposedHelpers.getIntField(musicActivity,"currentPosition");
        int duration = XposedHelpers.getIntField(musicActivity,"currentDuration");
        //
        Log.d(TAG,"position="+position);
        Log.d(TAG,"duration="+duration);
        // ��������� ����� �� ��������� ������ �����
        if (position > back_press_time*1000)
          position = position - back_press_time*1000;
        else
          position = 0;
        if (duration > 0)
        {
          Log.d(TAG,"set position to "+position);
          XposedHelpers.callMethod(musicActivity, "setPosition", (int)(100*position/duration));
          Log.d(TAG,"position changed OK");
          Toast.makeText(musicActivity, "������� �� "+back_press_time+" ������", Toast.LENGTH_SHORT).show();
        }
      }
    }
  };

  // ������ receiver ������������ ������ � �����-������
  private void createReceivers()
  {
    // ���������� MEDIA_MOUNT/UNMOUNT/EJECT
    usbReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(musicActivity,"UsbCardBroadCastReceiver");
    if (usbReceiver != null)
  	{
      // ��������� receiver �� ������������ ������
      musicActivity.unregisterReceiver(usbReceiver);
      // �������� ��� ������, ������� ������� �� ����������
      musicActivity.registerReceiver(usbReceiver, new IntentFilter());
      // �������� ���� ����������
      IntentFilter ui = new IntentFilter();
      // ���������� ��������� media
      ui.addAction(Intent.ACTION_MEDIA_MOUNTED);
      ui.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
      ui.addAction(Intent.ACTION_MEDIA_EJECT);
      ui.addDataScheme("file");
      musicActivity.registerReceiver(mediaReceiver, ui);
      Log.d(TAG,"UsbCardBroadCastReceiver changed");
    }
    if (control_keys)
    {
      // ���������� media ������
      IntentFilter mi = new IntentFilter();
      mi.addAction(Intent.ACTION_MEDIA_BUTTON);
      mi.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
      musicActivity.registerReceiver(mediaButtonReceiver, mi);
      Log.d(TAG,"MediaButtonReceiver created");
      // ���������� com.android.music.musicservicecommand
      IntentFilter ci = new IntentFilter();
      ci.addAction("com.android.music.musicservicecommand");
      musicActivity.registerReceiver(commandReceiver, ci);
      Log.d(TAG,"com.android.music.* receivers created");
    }
    // ���������� com.android.music.querystate
    IntentFilter qi = new IntentFilter();
    qi.addAction("com.android.music.querystate");
    musicActivity.registerReceiver(tagsQueryReceiver, qi);
  }
  
  // ������� ������� play/pause
  private void sendNotifyIntent(Context context)
  {
    Intent intent = new Intent("com.android.music.playstatechanged");
    addMp3Tags(intent);
    // context.sendBroadcast(intent);
    context.sendOrderedBroadcast(intent, null);
    Log.d(TAG,"com.android.music.playstatechanged sent");
  }
  
  // ������ � ���������� mp3-�����
  private Runnable mp3Info = new Runnable()
  {
    public void run() 
    {
      Log.d(TAG,"mp3Info run: "+currentFileName);
      readMp3Infos(musicActivity, currentFileName);
      showMp3Infos();
      sendNotifyIntent(musicActivity);
    }
  };
  
  // ������ ����� mp3-�����
  private void readMp3Infos(Context context, String fileName)
  {
    // ���� ��� ����� ���������
    if (!fileName.isEmpty())
    {
      String[] names = { fileName };
      Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] { "title", "duration", "artist", "_id", "album", "_display_name", "_data", "album_id", "_size" }, "_data=?", names, "title_key");
      try
      {
        if (cursor.moveToFirst())
        {
          Log.d(TAG,"moveToFirst OK");
          album = cursor.getString(cursor.getColumnIndex("album"));
          Log.d(TAG,"album="+album);
          title = cursor.getString(cursor.getColumnIndex("title"));
          Log.d(TAG,"title="+title);
          artist = cursor.getString(cursor.getColumnIndex("artist"));
          if (artist.equals("<unknown>")) artist = "";
          Log.d(TAG,"artist="+artist);
          album_id = cursor.getLong(cursor.getColumnIndex("album_id"));
          Log.d(TAG,"album_id="+album_id);
          // ����������� ��������
          try
          {
            Uri uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), album_id);
            cover = MediaStore.Images.Media.getBitmap(musicActivity.getContentResolver(), uri);
          }
          catch (Exception e)
          {
            cover = null;
          }
          if (cover == null)
          {
            // ������� ��� � �����
            Log.d(TAG,"bitmap notfound in tags");
            // �������� �� �������� <album_cover_name>.jpg
            File f = new File(fileName);
            String coverFileName = f.getParent()+"/"+album_cover_name;
            Log.d(TAG,"cover="+coverFileName);
            // ��������� ��������
            cover = BitmapFactory.decodeFile(coverFileName);
            // ���� ��� ����� ������ �� ���������
          }
        }
        else
          Log.w(TAG,"moveToFirst false");
      }
      catch (Exception e)
      {
        Log.e(TAG,e.getMessage());
      }
      finally
      {
        cursor.close();
      }
    }
    else
    {
      album = "";
      title = "";
      artist = "";
      album_id = -1;
      cover = null;
    }
  }
  
  // ����� �����
  private void showMp3Infos()
  {
	// title
	if (titleView != null)
		titleView.setText(title);
	// album
	if (albumView != null)
	  albumView.setText(album);
	// artist
	if (artistView != null)
	  artistView.setText(artist);
	// cover
	if (coverView != null)
	{
	  coverView.setImageBitmap(cover);
	  if (cover != null)
      {
        coverView.setVisibility(View.VISIBLE);
        if (visualizerView != null)
      	  visualizerView.setVisibility(View.INVISIBLE);
      }
      else
      {
        coverView.setVisibility(View.INVISIBLE);
        if (visualizerView != null)
          visualizerView.setVisibility(View.VISIBLE);
      }
	}
  }  
  
  // ���� � intent extras
  private void addMp3Tags(Intent intent)
  {
    intent.putExtra("artist", artist);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
    intent.putExtra("album", album);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
    intent.putExtra("album_id", album_id);
    intent.putExtra("track", title);
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, title);
    intent.putExtra("playstate", mPlaying);
    intent.putExtra("playing", mPlaying);
    intent.putExtra("cover", cover);
  }
  
  // ���������� MEDIA_MOUNT/UNMOUNT/EJECT
  private BroadcastReceiver mediaReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction(); 
      Log.d(TAG,"media receiver:"+action);
      if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
      {
    	// �� �������� ����������� ����������
        String file_name = "";
        // ������������ ������ ���������
        XposedHelpers.callMethod(musicActivity, "Updata_DevList");
        int currentDev = XposedHelpers.getIntField(musicActivity, "currentDev");
        Object mUi = XposedHelpers.getObjectField(musicActivity, "mUi");
        XposedHelpers.callMethod(mUi, "updateDevList", currentDev);
        // ����������� ������, �����
        String drivePath = intent.getData().getPath();
        Log.d(TAG,"ACTION_MEDIA_MOUNTED: "+drivePath);
        // ������ �������������� �����
        int currentPlayIndex = XposedHelpers.getIntField(musicActivity, "currentPlayIndex");
        Log.d(TAG,"currentPlayIndex="+currentPlayIndex);
        if (currentPlayIndex == -1)
        {
          // ������ ������ ����, �������� ����������� ������ ������
          @SuppressWarnings("unchecked")
          ArrayList<String> list = (ArrayList<String>)XposedHelpers.callMethod(musicActivity, "loadList", "playlist");
          Log.d(TAG,"list.size="+list.size());
          if (list.size() > 0)
          {
            // ������ ���� � ������
            file_name = (String)list.get(0);
            Log.d(TAG,file_name);
            if (file_name.startsWith(drivePath))
            {
              Log.d(TAG,"continue play");
              // ������������ ����� �� ������� ��������� ������������� ����
              XposedHelpers.callMethod(musicActivity, "ReadLocalData");
              XposedHelpers.callMethod(musicActivity, "MusicOncePlay");
              return;
            }
          }
          else
          {
            // ���� ��������� ������ ������ ��� ��� ����������� �������� ����� �� �� �����������, ��. ���������
            int automedia_enable = Settings.System.getInt(context.getContentResolver(),"MusicAutoPlayEN",0);
            Log.d(TAG,"automedia_enable="+automedia_enable);
            if (automedia_enable == 0) return;
            // ���� 1 �������� ������� ����������
          }
        }
        else
        {
          // ���� ��� �������������
          return;
        }
      }
      // �������� ���������� �� ���������
      Log.d(TAG,"call default usbReceiver");
      usbReceiver.onReceive(context, intent);
    }
  };
  
  // ���������� android.intent.action.MEDIA_BUTTON
  private BroadcastReceiver mediaButtonReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      int keyCode = event.getKeyCode();
      Log.d(TAG,"android.intent.action.MEDIA_BUTTON: "+keyCode);
      if (event.getAction() == KeyEvent.ACTION_DOWN)
      {
    	// ����������� ������� ������
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
          XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY)
        {
          if (mPlaying == false) XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        }
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)
        {
          if (mPlaying == true) XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        }
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)
          XposedHelpers.callMethod(musicActivity, "cmdNext");
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
          XposedHelpers.callMethod(musicActivity, "cmdPrev");
      }
      abortBroadcast();
    }
  };
  
  // ���������� com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      // ��������� mp3-����
      Log.d(TAG,"Music: tags query receiver");
      sendNotifyIntent(context);
    }
  };
  
 // ���������� com.android.music.musicservicecommand
 private BroadcastReceiver commandReceiver = new BroadcastReceiver()
 {

   public void onReceive(Context context, Intent intent)
   {
     String cmd = intent.getStringExtra("command");
     Object mServer = XposedHelpers.getObjectField(musicActivity, "mServer");
     int mPlaying = (int)XposedHelpers.callMethod(mServer, "getplaystate");
     if (cmd.equals("previous"))
       XposedHelpers.callMethod(mServer, "sPrev");
     else if (cmd.equals("next"))
       XposedHelpers.callMethod(mServer, "sNext");
     else if (cmd.equals("play"))
       if (mPlaying == 2) XposedHelpers.callMethod(mServer, "sPlayPause");
     else if (cmd.equals("pause"))
       if (mPlaying == 1) XposedHelpers.callMethod(mServer, "sPlayPause");
     else if (cmd.equals("stop"))
       XposedHelpers.callMethod(mServer, "sStop");
     else if (cmd.equals("toggleplay"))
       XposedHelpers.callMethod(mServer, "sPlayPause");
     else
       Log.w(TAG,"unknown command: "+cmd);
   }
 };

}
