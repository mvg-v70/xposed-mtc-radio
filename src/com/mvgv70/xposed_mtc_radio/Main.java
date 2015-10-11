package com.mvgv70.xposed_mtc_radio;

import com.mvgv70.utils.IniFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class Main implements IXposedHookLoadPackage
{

  private static int mFreq;
  private static Activity radioActivity;
  public static Service radioService;
  private static Object mUi;
  private static Handler handler;
  private static IniFile props = null;
  private static boolean titleEnable = false; 
  private static boolean buttonsEnable = false;
  private static boolean rdsDisable = false;
  private final static String TAG = "xposed-mtc-radio";
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-radio/mtc-radio.ini";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // RadioActivity.onCreate(Bundle)
    XC_MethodHook onCreateActivity = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"Activity:onCreate");
      	radioActivity = ((Activity)param.thisObject);
        // показать версию модуля
        try 
        {
     	  Context context = radioActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
     	} catch (NameNotFoundException e) {}
      	// переменныеs
      	mUi = XposedHelpers.getObjectField(param.thisObject, "mUi");
      	handler = (Handler)XposedHelpers.getObjectField(param.thisObject, "handler");
      	// RDS
      	AudioManager am = ((AudioManager)radioActivity.getSystemService(Context.AUDIO_SERVICE));
      	rdsDisable = am.getParameters("cfg_rds=").equals("0");
      	Log.d(TAG,"rdsDisable="+rdsDisable);
      	// читаем список радиостанций
      	readStationList();
      }
    };
    
    // RadioService.onCreate()
    XC_MethodHook onCreateService = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"Service:onCreate");
    	radioService = (Service)param.thisObject;
      }
    };
    
    // RadioActivity.showBandChannel()
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
    	mFreq = (int)param.args[0];
      }
    };
    
    // RadioService.showRds()
    XC_MethodHook showRds = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // если RDS выключен установим наименование радиостанции
        if (rdsDisable && titleEnable)
          handler.postDelayed(setStationName,100);
      }
    };
    
    // BtnChannel.SetFreqText(String)
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
          Log.d(TAG,"freq="+strFreq+" -> "+freqName+" -> "+freqName);
          if (!freqName.isEmpty())
            // подменим параметр с наименованием станции	
        	param.args[0] = freqName;
        }
      }
    };
    
    // start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreateActivity);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "showBandChannel", showBandChannel);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "onCreate", onCreateService);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "setFreq", int.class, setFreq);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "showRds", showRds);
    try
    {
      XposedHelpers.findAndHookMethod("com.microntek.radio.BtnChannel", lpparam.classLoader, "SetFreqText", String.class, btnSetFreqText);
    }
    catch (Error e) { Log.d(TAG,"error: BtnChannel.SetFreqText error: "+e.getMessage()); }
    Log.d(TAG,"com.microntek.radio hook OK");
  }
  
  // чтение списка радиостанций
  private void readStationList()
  {
    try
    {
      Log.d(TAG,"inifile load from "+INI_FILE_NAME);
      props = new IniFile();
      props.loadFromFile(INI_FILE_NAME);
      Log.d(TAG,"ini file loaded");
      // как показываем имена радиостанций
      titleEnable = props.getBoolValue("controls", "title", true);
      buttonsEnable = props.getBoolValue("controls", "buttons", true);
      Log.d(TAG,"titleEnable="+titleEnable+", buttonsEnable="+buttonsEnable);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  // форматированная частота радиостанции
  private String getFrequencyString(int freq)
  {
    return (String)XposedHelpers.callMethod(mUi, "getFreqString", new Object[] {Integer.valueOf(freq)});
  }
  
  // показать наименование радиостанции
  private void showStationName(int freq)
  {
    String strFreq = getFrequencyString(freq);
    String freqName = getLongStationName(strFreq);
    Log.d(TAG,"freq="+freq+" -> "+strFreq+" -> "+freqName);
    XposedHelpers.callMethod(mUi, "showRadioPsn", new Object[] {freqName});
  }
  
  // наименование радиостанции
  private String getLongStationName(String freq)
  {
    return props.getValue("title",freq);
  }
  
  // короткое наименование радиостанции
  private String getShortStationName(String freq)
  {
	String result = "";
    result = props.getValue("buttons",freq);
    if (result.isEmpty())
      // если нет короткого поищем длинное
      result = props.getValue("title",freq);
    return result;
  }
  
  // изменение текста на кнопках
  private void showStationButtons()
  {
	Object button;
	Log.d(TAG,"showStationButtons begin");
	// если находимся в режиме поиска, то кнопки не переименовываем
	boolean mSearching = XposedHelpers.getBooleanField(radioService, "mSearching");
	Log.d(TAG,"mSearching="+mSearching);
	if (mSearching) return;
    int mBand = XposedHelpers.getIntField(radioService,"mBand");
    Log.d(TAG,"mBand="+mBand);
    int[][] freq = (int[][])XposedHelpers.getObjectField(radioService,"freq");
    Log.d(TAG,"freq OK");
    // цикл по кнопкам
    for (int i = 0; i < 30; i++)
    {
      // поиск кнопки
      button = XposedHelpers.callMethod(mUi, "getChannelButton", new Object[] {Integer.valueOf(i)});
      if (button!= null)
        Log.d(TAG,"button="+button.getClass().getName());
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
  
  private final Runnable setStationName = new Runnable()
  {
    public void run() 
    {
      showStationName(mFreq);
    }
  };

};
