package com.mvgv70.xposed_mtc_radio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import com.mvgv70.utils.IniFile;
import com.mvgv70.utils.Utils;
import com.mvgv70.xposed_mtc_radio.StationList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;

public class Main implements IXposedHookLoadPackage
{

  private static int mFreq;
  private static String strFreq = "";
  private static String freqName = "";
  private static String rdsInfo = "";
  private static Activity radioActivity;
  private static boolean active_flag = false;
  private static boolean ss_flag = false;
  private static Toast toast = null;
  private static Service radioService;
  private static OnClickListener mUi;
  private static SharedPreferences radio_prefs; 
  private static Context context;
  private static IniFile props = new IniFile();
  private static boolean titleEnable = false; 
  private static boolean buttonsEnable = false;
  private static boolean remapSearch = true;
  private static boolean toastEnable = false;
  private static boolean amEnable = true;
  private static int toastSize = 0;
  private static boolean rdsDisable = false;
  private static int freqBands = 4;
  private static int freqButtons = 6;
  private static String EXTERNAL_SD = "/mnt/external_sd/";
  private static String INI_FILE_NAME = EXTERNAL_SD+"mtc-radio/mtc-radio.ini";
  private static final String STATION_LIST_FILE = "/com.microntek.radio_preferences.txt";
  private static final String MAIN_SECTION = "controls";
  private static final String TITLE_SECTION = "title";
  private static final String BUTTON_SECTION = "buttons";
  private static final String MEMORY_SECTION = "memory";
  private static final int MENU_ITEM_STATION_LIST = 1;
  private static final int MENU_ITEM_SAVE_SETTINGS = 2;
  private static final int MENU_ITEM_RESTORE_SETTINGS = 3;
  private final static String TAG = "xposed-mtc-radio";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // RadioActivity.onCreate(Bundle)
    XC_MethodHook onCreateActivity = new XC_MethodHook() {
	  
	  @SuppressLint("ShowToast")
	  @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Activity:onCreate");
        radioActivity = ((Activity)param.thisObject);
        active_flag = true;
        // �������� ������ ������
        try 
        {
          context = radioActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          // ������ ������
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
          Log.d(TAG,"android "+Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {}
        // ������������ ������������ ����� �� build.prop
        EXTERNAL_SD = Utils.getModuleSdCard();
        INI_FILE_NAME = EXTERNAL_SD+"mtc-radio/mtc-radio.ini";
      	// ����������
      	mUi = (OnClickListener)Utils.getObjectField(param.thisObject, "mUi");
      	// RDS
      	AudioManager am = ((AudioManager)radioActivity.getSystemService(Context.AUDIO_SERVICE));
      	rdsDisable = am.getParameters("cfg_rds=").equals("0");
      	Log.d(TAG,"rdsDisable="+rdsDisable);
      	// ������ ������ ������������
      	readStationList();
        // ������ ������
      	int btn_search_id = radioActivity.getResources().getIdentifier("btn_search","id", radioActivity.getPackageName());
      	Log.d(TAG,"btn_search_id="+btn_search_id);
      	if (remapSearch)
      	{
      	  Log.d(TAG,"remap search");
      	  View btnSearch = radioActivity.findViewById(btn_search_id);
          if (btnSearch != null)
          {
            // �������� ���������� �������
            btnSearch.setOnClickListener(null);
            btnSearch.setClickable(false);
            // ������������� ���������� �������� �������
            btnSearch.setLongClickable(true);
            btnSearch.setOnLongClickListener(searchLongClick);
      	    Log.d(TAG,"btnSearch remap OK");
      	  }
          else
            Log.d(TAG,"btnSearch not found");
      	}
        // ���������� com.android.music.playstatusrequest
        IntentFilter qi = new IntentFilter();
        qi.addAction("com.android.music.playstatusrequest");
        radioActivity.registerReceiver(tagsQueryReceiver, qi);
        // ���������� �������� screen saver
        IntentFilter si = new IntentFilter();
        si.addAction("com.microntek.musicclockreset");
        radioActivity.registerReceiver(endClockReceiver, si);
        // toast
        toast = Toast.makeText(radioActivity,"",Toast.LENGTH_SHORT);
      }
    };
    
    // RadioActivity.onStop()
    XC_MethodHook onStopActivity = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Activity:onStop");
        active_flag = false;
      }
    };
    
    // RadioActivity.onResume()
    XC_MethodHook onResumeActivity = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Activity:onResume");
        ss_flag = false;
        active_flag = true;
      }
    };
    
    // RadioActivity.onDestroy()
    XC_MethodHook onDestroyActivity = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Activity:onDestroy");
        radioActivity.unregisterReceiver(endClockReceiver);
        active_flag = false;
        toast = null;
        radioActivity = null;
      }
    };
    
    // RadioService.onCreate()
    XC_MethodHook onCreateService = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Service:onCreate");
        radioService = (Service)param.thisObject;
        radio_prefs = (SharedPreferences)Utils.getObjectField(radioService, "app_preferences");
        // freq min/max
        int[][] freq = (int[][])Utils.getObjectField(radioService,"freq");
        freqBands = freq.length;
        freqButtons = freq[0].length;
        Log.d(TAG,"freqBands="+freqBands);
        Log.d(TAG,"freqButtons="+freqButtons);
      }
    };
    
    // RadioActivity.showBandChannel() - ������� �� ������� KGL
    XC_MethodHook showBandChannel = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"showBandChannel");
        // ���� RDS ��������
        if (rdsDisable && buttonsEnable)
          showStationButtons();
      }
    };
    
    // RadioService.setFreq(int)
    XC_MethodHook setFreq = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // �������� ������� ������� ������������
        mFreq = (int)param.args[0];
        strFreq = getFrequencyString(mFreq);
        freqName = getLongStationName(strFreq);
        rdsInfo = "";
        Log.d(TAG,"setFreq. freq="+mFreq+" -> "+strFreq+" -> "+freqName);
        // ����������� ���������
        showToast();    
        // ������� ���������� � ������������
        sendFreqInfo(radioActivity);
      }
    };
    
    // RadioService.resetRadioPreference()
    XC_MethodHook resetRadioPreference = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"resetRadioPreference");
        int i = 0;
        int freqInt;
        String memoryKey;
        String freqValue;
        readStationList();
        Editor editor = radio_prefs.edit();
        int[][] freq = (int[][])Utils.getObjectField(radioService,"freq");
        // TODO: ���� �� ���������� � �������
        for (int mBand = 1; mBand <= freqBands; mBand++)
          for (int index = 1; index <= freqButtons; index ++)
          {
            memoryKey = "P"+mBand+"."+index;
            freqValue = props.getValue(MEMORY_SECTION, memoryKey, "");
            if (!freqValue.isEmpty())
            {
              Log.d(TAG,memoryKey+"="+freqValue);
              try
              {
                freqInt = (int)(Float.valueOf(freqValue)*1000000);
                Log.d(TAG,"freqInt="+freqInt+", i="+i);
                freq[mBand-1][index-1] = freqInt;
                editor.putInt("RadioFrequency"+i, freqInt);
              } catch (Exception e) { }
            }
            i++;
          }
        Utils.setObjectField(radioService,"freq",freq);
        editor.commit();
        Log.d(TAG,"resetRadioPreference OK");
      }
    };
    
    // RadioActivity.showRds()
    XC_MethodHook showRds = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // ���������� RDS
        Log.d(TAG,"showRds");
        // ��������� ������������ ������������
        if (titleEnable && rdsDisable)
          showStationName();
        if (!rdsDisable)
        {
          rdsInfo = (String)Utils.getObjectField(radioService, "radioPsn");
          Log.d(TAG,"rdsInfo="+rdsInfo);
          // ������� ���������� � ������������
          sendFreqInfo(radioActivity);
        }
      }
    };
    
    // BtnChannel.SetFreqText(String) - ������� �� ������� �� KGL
    XC_MethodHook btnSetFreqText = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        // ���� RDS �������� 
        if (rdsDisable && buttonsEnable)
        {
          boolean mSearching = Utils.getBooleanField(radioService, "mSearching");
          if (mSearching) return;
          // ������������� ������������ ������� �� ������-layout 
          String strFreq = (String)param.args[0];
          String freqName = getShortStationName(strFreq);
          // Log.d(TAG,"freq="+strFreq+" -> "+freqName);
          if (!freqName.isEmpty())
            // �������� �������� � ������������� �������	
            param.args[0] = freqName;
          
        }
      }
    };
    
    // Activity.onCreateOptionsMenu(Menu)
    XC_MethodHook onCreateOptionsMenu = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreateOptionsMenu");
        if (radioActivity.hashCode() == param.thisObject.hashCode())
        {
          Menu menu = (Menu)param.args[0];
          menu.add(1,MENU_ITEM_STATION_LIST,1,context.getString(R.string.station_list_menu_item));
          menu.add(2,MENU_ITEM_SAVE_SETTINGS,2,context.getString(R.string.save_settings_menu_item));
          menu.add(2,MENU_ITEM_RESTORE_SETTINGS,3,context.getString(R.string.restore_settings_menu_item));
          param.setResult(true);
        }
      }
    };
    
    // Activity.onOptionsItemSelected(MenuItem)
    XC_MethodHook onOptionsItemSelected = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        MenuItem menuItem = (MenuItem)param.args[0];
        Log.d(TAG,"onOptionsItemSelected."+menuItem.getItemId());
        if (radioActivity.hashCode() == param.thisObject.hashCode())
        {
          switch (menuItem.getItemId())
          {
            case MENU_ITEM_STATION_LIST:
              // ������ ������������ 
              showStationList();
              break;
            case MENU_ITEM_SAVE_SETTINGS:
              // ��������� ���������
              confirmDialog(true, context.getString(R.string.save_settings_query));
              break;
            case MENU_ITEM_RESTORE_SETTINGS:
              // ������������ ���������
              confirmDialog(false, context.getString(R.string.restore_settings_query));
              break;
          }
          param.setResult(true);
        }
      }
    };
    
    // Activity.onActivityResult(int,int,Intent)
    XC_MethodHook onActivityResult = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onActivityResult");
        if (radioActivity.hashCode() == param.thisObject.hashCode())
        {
          int requestCode = (int)param.args[0];
          int resultCode = (int)param.args[1];
          Intent data = (Intent)param.args[2];
          if (resultCode == Activity.RESULT_OK)
          {
            if (requestCode == 1)
            {
              // ������ ������������
              gotoStation(data);
            }
          }
        }
      }
    };
    
    // RadioActivity.onNextBand()
    XC_MethodHook onNextBand = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onNextBand");
        if (!amEnable)
        {
          Boolean mSearching = Utils.getBooleanField(radioService, "mSearching");
          if (mSearching) Utils.callMethod(radioService, "searchStop");
          int mBand = Utils.getIntField(radioService, "mBand");
          Log.d(TAG,"mBand="+mBand);
          int mChannel = Utils.getIntField(radioService, "mChannel");
          // ���������� ��������� AM
          mBand = (mBand + 1) % 3;
          Log.d(TAG,"mBand=>"+mBand);
          Utils.callMethod(radioService, "toBandChannel", mBand, mChannel);
          // �� �������� ����������� ����������
          param.setResult(null);
        }
      }
    };
    
    // start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    // ������ ����� ��������������� �������
    Utils.setTag(TAG);
    Utils.readXposedMap();
    // �������� �������
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreateActivity);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onPause", onStopActivity);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onResume", onResumeActivity);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onDestroy", onDestroyActivity);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "showBandChannel", showBandChannel);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "showRds", showRds);
    Utils.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onNextBand", onNextBand);
    Utils.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "onCreate", onCreateService);
    Utils.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "setFreq", int.class, setFreq);
    Utils.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "resetRadioPreference", resetRadioPreference);
    Utils.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onCreateOptionsMenu", Menu.class, onCreateOptionsMenu);
    Utils.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onOptionsItemSelected", MenuItem.class, onOptionsItemSelected);
    Utils.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onActivityResult", int.class, int.class, Intent.class, onActivityResult);
    try
    {
      Utils.findAndHookMethod("com.microntek.radio.BtnChannel", lpparam.classLoader, "SetFreqText", String.class, btnSetFreqText);
    }
    catch (Error e) {}
    Log.d(TAG,"com.microntek.radio hook OK");
  }
  
  // ��� ������������ �����
  public static String getIniFileName()
  {
    return INI_FILE_NAME;
  }
  
  // ������ ������ ������������
  private void readStationList()
  {
    String iniFile = getIniFileName();
    try
    {
      Log.d(TAG,"inifile load from "+iniFile);
      props.clear();
      props.loadFromFile(iniFile);
      Log.d(TAG,"ini file loaded, line count="+props.linesCount("title"));
      // ���������
      titleEnable = props.getBoolValue(MAIN_SECTION, "title", true);
      buttonsEnable = props.getBoolValue(MAIN_SECTION, "buttons", true);
      remapSearch = props.getBoolValue(MAIN_SECTION, "search", true);
      toastEnable = props.getBoolValue(MAIN_SECTION, "toast", false);
      toastSize = props.getIntValue(MAIN_SECTION, "toast.size", 0);
      amEnable = props.getBoolValue(MAIN_SECTION, "am.band", true);
      Log.d(TAG,"title="+titleEnable);
      Log.d(TAG,"buttons="+buttonsEnable);
      Log.d(TAG,"search="+remapSearch);
      Log.d(TAG,"toast="+toastEnable);
      Log.d(TAG,"toast.Size="+toastSize);
      Log.d(TAG,"am.band="+amEnable);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  // ��������������� ������� ������������
  private String getFrequencyString(int freq)
  {
    return (String)Utils.callMethod(mUi, "getFreqString", freq);
  }
  
  // �������� ������������ ������������
  private void showStationName()
  {
    // ���� RDS ��������
    Utils.callMethod(mUi, "showRadioPsn", freqName);
  }
  
  // �������� ����������� � ����� �������
  private static void showToast()
  {
    // toast ���������� ������, ���� ����� �� ������� � �� � �����������
    if (!active_flag && toastEnable && !ss_flag)
    {
      Log.d(TAG,"showToast()");
      if (toast == null) Log.d(TAG,"toast == null");
      ViewGroup group = (ViewGroup)toast.getView();
      if (group == null) Log.d(TAG,"group == null");
      TextView toastText = (TextView)group.getChildAt(0);
      // toast size
      if (toastSize > 0) toastText.setTextSize(toastSize);
      // toast text
      toastText.setText(strFreq+" "+freqName);
      toast.setDuration(Toast.LENGTH_SHORT);
      toast.show();
    }
  }
  
  // ������������ ������������
  private String getLongStationName(String freq)
  {
    return props.getValue(TITLE_SECTION,freq);
  }
  
  // �������� ������������ ������������
  private String getShortStationName(String freq)
  {
    String result = "";
    result = props.getValue(BUTTON_SECTION,freq);
    if (result.isEmpty())
      // ���� ��� ��������� ������ �������
      result = props.getValue(TITLE_SECTION,freq);
    return result;
  }
  
  // ��������� ������ �� �������
  private void showStationButtons()
  {
    Object button;
    Log.d(TAG,"showStationButtons");
    // ���� ��������� � ������ ������, �� ������ �� ���������������
    boolean mSearching = Utils.getBooleanField(radioService, "mSearching");
    if (mSearching) return;
    int mBand = Utils.getIntField(radioService,"mBand");
    int[][] freq = (int[][])Utils.getObjectField(radioService,"freq");
    // TODO: ���� �� �������
    for (int i = 0; i < freqButtons; i++)
    {
      // ����� ������
      button = Utils.callMethod(mUi, "getChannelButton", i);
      if (button == null) return;
      // �������, ���� ��� �� ������, � layout
      if (!(button instanceof Button)) return;
      // ������� ������
      int buttonFreq = freq[mBand][i];
      // ����������� �������
      String freqStr = getFrequencyString(buttonFreq);
      // �������� ������������
      String text = getShortStationName(freqStr);
      Log.d(TAG,"freq="+freqStr+", text="+text);
      if (!text.isEmpty())
        // ������� ����� �� ������, ���� �� �����
        ((Button)button).setText(text);
    }
  }
  
  private void showStationList()
  {
    String freq;
    String name;
    ArrayList<String> freqList = new ArrayList<String>();  
    ArrayList<String> nameList = new ArrayList<String>();
    // ���� �� ������ ������������
    IniFile.KeyIterator lines = props.enumKeys(TITLE_SECTION);
    while (lines.hasNext()) 
  	{
  	  freq = lines.next();
  	  name = props.getValue(TITLE_SECTION, freq);
  	  freqList.add(freq);
  	  nameList.add(freq+" "+name);
  	}
    if (freqList.size() > 0)
    {
      Intent intent = new Intent(context, StationList.class);
      intent.putStringArrayListExtra("freq", freqList);
      intent.putStringArrayListExtra("name", nameList);
      String sFreq = getFrequencyString(mFreq);
      Log.d(TAG,"sFreq="+sFreq);
      intent.putExtra("current", sFreq);
      radioActivity.startActivityForResult(intent, 1);
    }
    else
    {
      Toast.makeText(radioActivity, context.getString(R.string.station_list_empty), Toast.LENGTH_SHORT).show();
      Log.w(TAG,context.getString(R.string.station_list_empty));
    }
  }
  
  // ������� �� ������������
  private void gotoStation(Intent data)
  {
    String freq = data.getStringExtra("frequency");
    Log.d(TAG,"freq="+freq);
    boolean mSearching = Utils.getBooleanField(radioService, "mSearching");
    if (mSearching) return;
    int freqInt = (int)(Float.valueOf(freq)*1000000);
    Log.d(TAG,"(int)freq="+freqInt);
    Utils.callMethod(radioService, "setFreq", freqInt);
    Utils.callMethod(radioService, "showBandChannel");
    Utils.callMethod(radioService, "showFreq");
    Utils.callMethod(radioService, "showRds");
    Utils.callMethod(radioService, "showSt");
    Utils.setIntField(radioService, "mChannel", -1);
    Log.d(TAG,"frequency set OK");
  }
  
  // ������ ������������� ����������� �������� �����
  private void confirmDialog(boolean save, String text) 
  {
    final boolean save_settings = save; 
  	AlertDialog.Builder builder = new AlertDialog.Builder(radioActivity);
    builder.setTitle(text);
    // OK
    builder.setPositiveButton("OK", 
      new DialogInterface.OnClickListener() 
      {
        public void onClick(DialogInterface dialog, int id) 
        {
          try
          {
            if (save_settings) 
              saveSettings();
            else
              restoreSettings();
            Toast.makeText(radioActivity, context.getString(R.string.settings_copy_success), Toast.LENGTH_SHORT).show();
          }
          catch (Exception e)
          {
            Log.e(TAG,e.getMessage());
            Toast.makeText(radioActivity, context.getString(R.string.settings_copy_error), Toast.LENGTH_SHORT).show();
          }
        }
      }
    );
    // show
    builder.setCancelable(true);
    builder.create();
    builder.show();
  }
  
  // ���������� ��������
  private void saveSettings() throws Exception
  {
    // ������ ��������� � ��������� � ����
    int freq;
    String fileName = radioActivity.getFilesDir()+STATION_LIST_FILE;
    Log.d(TAG,"save settings to "+fileName);
    BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
    try 
    {
      // ���� �� ���� ��������
      for (int i = 0; i < freqBands*freqButtons; i++)
      {
        freq = radio_prefs.getInt("RadioFrequency"+i, 87500000);
        bw.write(String.valueOf(freq));
        bw.newLine();
      }
    }
    finally 
    {
      bw.close();
    }
  }
  
  // �������������� ��������
  private void restoreSettings() throws Exception
  {
    // ������ ���� � ��������� ��������� �����
    String freq;
    String fileName = radioActivity.getFilesDir()+STATION_LIST_FILE;
    Log.d(TAG,"restore settings from "+fileName);
    Editor editor = radio_prefs.edit();
    BufferedReader br = new BufferedReader(new FileReader(fileName));
    try 
    {
      // ���� �� ���� ��������
      for (int i = 0; i < freqBands*freqButtons; i++)
      {
        freq = br.readLine();
        Log.d(TAG,"freq("+i+")="+freq);
        if (!freq.isEmpty())
          editor.putInt("RadioFrequency"+i, Integer.valueOf(freq));
      }
      editor.commit();
    }
    finally 
    {
      br.close();
    }
    // ��������� �����
    radioActivity.finish();
    // Utils.callMethod(radioService, "loadPreference");
  }
  
  // ���������� �������� ������� �� ������ ������
  private OnLongClickListener searchLongClick = new OnLongClickListener()
  {
    public boolean onLongClick(View v)
    {
      if (mUi != null) mUi.onClick(v);
      return true;
    }
  };
  
  private void sendFreqInfo(Context context)
  {
    if (context == null) return; 
    Intent intent = new Intent("com.android.radio.freq");
    intent.putExtra("freq", strFreq);
    // � ������ RDS ������ RDS-����������
    if (rdsDisable)
      intent.putExtra("freq_name", freqName);
    else
      intent.putExtra("freq_name", rdsInfo);
    context.sendBroadcast(intent);
  }
  
  // ���������� com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  { 
    public void onReceive(Context context, Intent intent)
    {
      // ��������� ���������� � �����������
      Log.d(TAG,"Radio: tags query receiver");
      ss_flag = true;
      sendFreqInfo(context);
    }
  };
  
  // ���������� ���������� Screen Saver
  private BroadcastReceiver endClockReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"Radio: end clock receiver");
      ss_flag = false;
    }
  };
    
};
