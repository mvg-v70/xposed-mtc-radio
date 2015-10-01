package com.mvgv70.xposed_mtc_radio;

import com.mvgv70.utils.IniFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class Main implements IXposedHookLoadPackage
{

  private static int mFreq;
  private static Activity mtcRadio;
  private static Object mUi;
  private static TextView psnIndictor;
  private static IniFile props = null;
  private static boolean rdsDisable = false;
  private final static String TAG = "xposed-mtc-radio";
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-radio/mtc-radio.ini";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // RadioActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreate");
      	mtcRadio = ((Activity)param.thisObject);
        // показать версию модуля
        try 
        {
     	  Context context = mtcRadio.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
     	} catch (NameNotFoundException e) {}
      	// переменныеs
      	mUi = XposedHelpers.getObjectField(param.thisObject, "mUi");
      	psnIndictor = (TextView)XposedHelpers.getObjectField(mUi, "psnIndictor");
      	// RDS
      	AudioManager am = ((AudioManager)mtcRadio.getSystemService(Context.AUDIO_SERVICE));
      	rdsDisable = am.getParameters("cfg_rds=").equals("0");
      	Log.d(TAG,"rdsDisable="+rdsDisable);
      	// читаем список радиостанций
      	readStationList();
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
        if (rdsDisable)
          psnIndictor.postDelayed(setStationName,100);
      }
    };
    
	// start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "setFreq", int.class, setFreq);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioService", lpparam.classLoader, "showRds", showRds);
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
	  Log.d(TAG,"ini file loaded, line count="+props.linesCount(""));
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
  
  // наименование радиостанции
  private String getStationName(String freq)
  {
    return props.getValue("",freq);
  }
  
  // показать наименование радиостанции
  private void showStationName(int freq)
  {
	String strFreq = getFrequencyString(freq);
    String freqName = getStationName(strFreq);
    Log.d(TAG,"freq="+freq+" -> "+strFreq+" -> "+freqName);
    XposedHelpers.callMethod(mUi, "showRadioPsn", new Object[] {freqName});
  }
  
  private Runnable setStationName = new Runnable()
  {
    public void run() 
    {
      showStationName(mFreq);
	}
  };

};
