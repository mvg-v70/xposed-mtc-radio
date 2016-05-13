package com.mvgv70.xposed_mtc_radio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import com.mvgv70.utils.IniFile;
import com.mvgv70.xposed_mtc_radio.StationList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
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
  private static Handler handler;
  private static Context context;
  private static IniFile props = new IniFile();
  private static boolean titleEnable = false; 
  private static boolean buttonsEnable = false;
  private static boolean remapSearch = true;
  private static boolean toastEnable = false;
  private static int toastSize = 0;
  private static boolean rdsDisable = false;
  private static String EXTERNAL_SD = "/mnt/external_sd/";
  private static final String INI_DIR = "mtc-radio";
  private static String INI_FILE_NAME = EXTERNAL_SD+INI_DIR+"/mtc-radio.ini";
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
	  
	  @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"Activity:onCreate");
      	radioActivity = ((Activity)param.thisObject);
      	active_flag = true;
      	int btn_search_id = radioActivity.getResources().getIdentifier("btn_search","id", radioActivity.getPackageName());
      	Log.d(TAG,"btn_search_id="+btn_search_id);
        // показать версию модуля
        try 
        {
          context = radioActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  // версия модуля
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
     	} catch (NameNotFoundException e) {}
      	// переменные
      	mUi = (OnClickListener)XposedHelpers.getObjectField(param.thisObject, "mUi");
      	handler = (Handler)XposedHelpers.getObjectField(param.thisObject, "handler");
      	// RDS
      	AudioManager am = ((AudioManager)radioActivity.getSystemService(Context.AUDIO_SERVICE));
      	rdsDisable = am.getParameters("cfg_rds=").equals("0");
      	Log.d(TAG,"rdsDisable="+rdsDisable);
      	// настройки карты с ini-файлом
      	readCardname();
      	// читаем список радиостанций
      	readStationList();
        // кнопка поиска
      	if (remapSearch)
      	{
      	  Log.d(TAG,"remap search");
      	  View btnSearch = radioActivity.findViewById(btn_search_id);
          if (btnSearch != null)
          {
            // заменяем обработчик нажатия
            btnSearch.setOnClickListener(null);
            btnSearch.setClickable(false);
            // устанавливаем обработчик длинного нажатия
            btnSearch.setLongClickable(true);
            btnSearch.setOnLongClickListener(searchLongClick);
      	    Log.d(TAG,"btnSearch remap OK");
      	  }
          else
            Log.d(TAG,"btnSearch not found");
      	}
        // обработчик com.android.music.playstatusrequest
        IntentFilter qi = new IntentFilter();
        qi.addAction("com.android.music.playstatusrequest");
        radioActivity.registerReceiver(tagsQueryReceiver, qi);
        // обработчик закрытия screen saver
        IntentFilter si = new IntentFilter();
        si.addAction("com.microntek.musicclockreset");
        radioActivity.registerReceiver(endClockReceiver, si);
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
    	radio_prefs = (SharedPreferences)XposedHelpers.getObjectField(radioService, "app_preferences");
      }
    };
    
    // RadioActivity.showBandChannel() - надписи на кнопках KGL
    XC_MethodHook showBandChannel = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"showBandChannel");
    	// если RDS выключен
    	if (rdsDisable && buttonsEnable)
    	  showStationButtons();
      }
    };
    
    // RadioService.setFreq(int)
    XC_MethodHook setFreq = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	// сохраним текущую частоту радиостанции
    	Log.d(TAG,"setFreq");
    	mFreq = (int)param.args[0];
        strFreq = getFrequencyString(mFreq);
        freqName = getLongStationName(strFreq);
        rdsInfo = "";
        Log.d(TAG,"freq="+mFreq+" -> "+strFreq+" -> "+freqName);
        // всплывающее сообщение
        showToast();    
        // послать информацию о радиостанции
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
        int[][] freq = (int[][])XposedHelpers.getObjectField(radioService,"freq");
        for (int mBand = 1; mBand <= 4; mBand++)
          for (int index = 1; index <= 6; index ++)
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
        XposedHelpers.setObjectField(radioService,"freq",freq);
        editor.commit();
        Log.d(TAG,"resetRadioPreference OK");
      }
    };
    
    // RadioService.showRds()
    XC_MethodHook showRds = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // установим наименование радиостанции
        if (titleEnable && rdsDisable) 
          handler.postDelayed(setStationName,100);
        // информация RDS
        Log.d(TAG,"showRds");
        if (!rdsDisable)
        {
          rdsInfo = (String)XposedHelpers.getObjectField(radioService, "radioPsn");
          Log.d(TAG,"rdsInfo="+rdsInfo);
          // послать информацию о радиостанции
          sendFreqInfo(radioActivity);
        }
      }
    };
    
    // BtnChannel.SetFreqText(String) - надписи на кнопках не KGL
    XC_MethodHook btnSetFreqText = new XC_MethodHook() {
	           
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    	// если RDS выключен 
        if (rdsDisable && buttonsEnable)
        {
          boolean mSearching = XposedHelpers.getBooleanField(radioService, "mSearching");
          if (mSearching) return;
          // устанавливаем наименование станции на кнопке-layout 
          String strFreq = (String)param.args[0];
          String freqName = getShortStationName(strFreq);
          Log.d(TAG,"freq="+strFreq+" -> "+freqName);
          if (!freqName.isEmpty())
            // подменим параметр с наименованием станции	
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
              // список радиостанций 
              showStationList();
              break;
            case MENU_ITEM_SAVE_SETTINGS:
              // сохранить настройки
              confirmDialog(true, context.getString(R.string.save_settings_query));
              break;
            case MENU_ITEM_RESTORE_SETTINGS:
              // восстановить настройки
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
              // список радиостанций
              gotoStation(data);
            }
          }
        }
      }
    };
    
    // start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreateActivity);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onStop", onStopActivity);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onResume", onResumeActivity);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onDestroy", onDestroyActivity);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "showBandChannel", showBandChannel);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "onCreate", onCreateService);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "setFreq", int.class, setFreq);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "showRds", showRds);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "resetRadioPreference", resetRadioPreference);
    XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onCreateOptionsMenu", Menu.class, onCreateOptionsMenu);
    XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onOptionsItemSelected", MenuItem.class, onOptionsItemSelected);
    XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onActivityResult", int.class, int.class, Intent.class, onActivityResult);
    try
    {
      XposedHelpers.findAndHookMethod("com.microntek.radio.BtnChannel", lpparam.classLoader, "SetFreqText", String.class, btnSetFreqText);
    }
    catch (Error e) {}
    Log.d(TAG,"com.microntek.radio hook OK");
  }
  
  // имя настроечного файла
  public static String getIniFileName()
  {
    return INI_FILE_NAME;
  }
  
  // чтение списка радиостанций
  private void readStationList()
  {
	String iniFile = getIniFileName();
    try
    {
      Log.d(TAG,"inifile load from "+iniFile);
      props.clear();
      props.loadFromFile(iniFile);
      Log.d(TAG,"ini file loaded, line count="+props.linesCount("title"));
      // настройки
      titleEnable = props.getBoolValue(MAIN_SECTION, "title", true);
      buttonsEnable = props.getBoolValue(MAIN_SECTION, "buttons", true);
      remapSearch = props.getBoolValue(MAIN_SECTION, "search", true);
      toastEnable = props.getBoolValue(MAIN_SECTION, "toast", false);
      toastSize = props.getIntValue(MAIN_SECTION, "toast.size", 0);
      Log.d(TAG,"title="+titleEnable);
      Log.d(TAG,"buttons="+buttonsEnable);
      Log.d(TAG,"search="+remapSearch);
      Log.d(TAG,"toast="+toastEnable);
      Log.d(TAG,"toast.Size="+toastSize);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  // форматированная частота радиостанции
  private String getFrequencyString(int freq)
  {
    return (String)XposedHelpers.callMethod(mUi, "getFreqString", freq);
  }
  
  // показать наименование радиостанции
  private void showStationName()
  {
    // если RDS выключен
    XposedHelpers.callMethod(mUi, "showRadioPsn", freqName);
  }
  
  // показать уведомление о смене станции
  private static void showToast()
  {
	// toast показываем только, если Радио не активно и не в скринейвере
    if (!active_flag && toastEnable && !ss_flag)
    {
      if (toast != null)
      {
        toast.cancel();
        toast = null;
      }
      toast = Toast.makeText(radioActivity, strFreq+" "+freqName, Toast.LENGTH_SHORT);
      if (toastSize > 0)
      {
        // toast size
        ViewGroup group = (ViewGroup)toast.getView();
        TextView messageTextView = (TextView)group.getChildAt(0);
        messageTextView.setTextSize(toastSize);
      }
      toast.show();
    }
  }
  
  // наименование радиостанции
  private String getLongStationName(String freq)
  {
    return props.getValue(TITLE_SECTION,freq);
  }
  
  // короткое наименование радиостанции
  private String getShortStationName(String freq)
  {
	String result = "";
    result = props.getValue(BUTTON_SECTION,freq);
    if (result.isEmpty())
      // если нет короткого поищем длинное
      result = props.getValue(TITLE_SECTION,freq);
    return result;
  }
  
  // изменение текста на кнопках
  private void showStationButtons()
  {
	Object button;
	Log.d(TAG,"showStationButtons");
	// если находимся в режиме поиска, то кнопки не переименовываем
	boolean mSearching = XposedHelpers.getBooleanField(radioService, "mSearching");
	if (mSearching) return;
    int mBand = XposedHelpers.getIntField(radioService,"mBand");
    int[][] freq = (int[][])XposedHelpers.getObjectField(radioService,"freq");
    // цикл по кнопкам
    for (int i = 0; i < 6; i++)
    {
      // поиск кнопки
      button = XposedHelpers.callMethod(mUi, "getChannelButton", i);
      if (button == null) return;
      // выходим, если это не кнопка, а layout
      if (!(button instanceof Button)) return;
      // частота кнопки
      int buttonFreq = freq[mBand][i];
      // форматируем частоту
      String freqStr = getFrequencyString(buttonFreq);
      // короткое наименование
      String text = getShortStationName(freqStr);
      Log.d(TAG,"freq="+freqStr+", text="+text);
      if (!text.isEmpty())
        // изменим текст на кнопке, если он задан
        ((Button)button).setText(text);
    }
  }
  
  private void showStationList()
  {
    String freq;
    String name;
    ArrayList<String> freqList = new ArrayList<String>();  
    ArrayList<String> nameList = new ArrayList<String>();
    // цикл по списку радиостанций
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
  
  // переход на радиостанцию
  private void gotoStation(Intent data)
  {
	String freq = data.getStringExtra("frequency");
	Log.d(TAG,"freq="+freq);
	boolean mSearching = XposedHelpers.getBooleanField(radioService, "mSearching");
    if (mSearching) return;
    int freqInt = (int)(Float.valueOf(freq)*1000000);
    Log.d(TAG,"(int)freq="+freqInt);
    XposedHelpers.callMethod(radioService, "setFreq", freqInt);
    // XposedHelpers.callMethod(radioService, "setMute", false);
    XposedHelpers.callMethod(radioService, "showBandChannel");
    XposedHelpers.callMethod(radioService, "showFreq");
    XposedHelpers.callMethod(radioService, "showRds");
    XposedHelpers.callMethod(radioService, "showSt");
    XposedHelpers.setIntField(radioService, "mChannel", -1);
    Log.d(TAG,"frequency set OK");
  }
  
  // диалог подтверждения копирования настроек радио
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
  
  // сохранение настроек
  private void saveSettings() throws Exception
  {
    // читаем настройки и сохраняем в файл
    int freq;
    String fileName = radioActivity.getFilesDir()+STATION_LIST_FILE;
    Log.d(TAG,"save settings to "+fileName);
    BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
    try 
    {
      for (int i=0; i<30; i++)
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
  
  // восстановление настроек
  private void restoreSettings() throws Exception
  {
    // читаем файл и сохраняем настройки радио
    String freq;
    String fileName = radioActivity.getFilesDir()+STATION_LIST_FILE;
    Log.d(TAG,"restore settings from "+fileName);
    Editor editor = radio_prefs.edit();
    BufferedReader br = new BufferedReader(new FileReader(fileName));
    try 
    {
      for (int i=0; i<30; i++)
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
    // закрываем Радио
    radioActivity.finish();
    // XposedHelpers.callMethod(radioService, "loadPreference");
  }
  
  // TODO: чтение имени карты, хранящей настройки
  private void readCardname()
  {
    try
    {
      // XSharedPreferences xp = new XSharedPreferences("com.mvgv70.xposed_mtc");
      XSharedPreferences xp = new XSharedPreferences(new File("/data/data/xposed-mtc.xml"));
      EXTERNAL_SD = xp.getString("external_sd","");
      Log.d(TAG,"EXTERNAL_SD.xml="+EXTERNAL_SD);
    }
    catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    if (EXTERNAL_SD.isEmpty()) EXTERNAL_SD = "/mnt/external_sd/";
    Log.d(TAG,"EXTERNAL_SD="+EXTERNAL_SD);
    INI_FILE_NAME = EXTERNAL_SD+INI_DIR+"/mtc-radio.ini";
  }
  
  private final Runnable setStationName = new Runnable()
  {
    public void run() 
    {
      showStationName();
    }
  };
  
  // обработчик длинного нажатия на кнопку поиска
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
	// TODO: если RadioActivity == null, то ничего не посылать
    if (context == null) return; 
    Intent intent = new Intent("com.android.radio.freq");
    intent.putExtra("freq", strFreq);
    // в режиме RDS пошлем RDS-информацию
    if (rdsDisable)
      intent.putExtra("freq_name", freqName);
    else
      intent.putExtra("freq_name", rdsInfo);
    context.sendBroadcast(intent);
  }
  
  // обработчик com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  { 

    public void onReceive(Context context, Intent intent)
    {
      // отправить информацию о радистанции
      Log.d(TAG,"Radio: tags query receiver");
      ss_flag = true;
      sendFreqInfo(context);
    }
  };
  
  // обработчик выключения Screen Saver
  private BroadcastReceiver endClockReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"Radio: end clock receiver");
      ss_flag = false;
    }
  };
    
};
