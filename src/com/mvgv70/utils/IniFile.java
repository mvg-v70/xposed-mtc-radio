package com.mvgv70.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import android.util.Log;

public class IniFile 
{
	
  HashMap<String,ArrayList<String>> ini_file = new HashMap<String,ArrayList<String>>();
	
  public void loadFromFile(String fileName) throws IOException
  {
    BufferedReader br;
    String line;
    String section = "";
    // ������ ������
    ini_file.put("", new ArrayList<String>());
    br = new BufferedReader(new FileReader(fileName));
    /*
      FileInputStream fis =  new FileInputStream("your_file_name.txt");
      BufferedReader r = new BufferedReader(new InputStreamReader(fis, "Cp1251"));
    */
    try 
    {
      while ((line = br.readLine()) != null)
      {
        // ��������� �� �������
        if (line.trim().isEmpty())
        {
          // ������ ������
        }
        else if (line.startsWith("#"))
        {
          // �����������
        }
        else if (line.startsWith(";"))
        {
          // �����������
        }
        else if (line.startsWith("["))
        {
          // ������
          section = line.substring(1,line.lastIndexOf("]")).trim();
          ini_file.put(section, new ArrayList<String>());
        }
        else
        {
          // ��������
          int equalIndex = line.indexOf("=");
          if (equalIndex > 0)
          {
            String key = line.substring(0,equalIndex).trim();
            String value = line.substring(equalIndex+1).trim();
            ini_file.get(section).add(key+"="+value);
         }
          else
           ini_file.get(section).add(line);
        }
      }
    }
    finally
    {
      br.close();
    }
  }
  
  public Iterator<String> enumSections()
  {
    return ini_file.keySet().iterator();
  }
  
  public Iterator<String> enumLines(String section)
  {
    return ini_file.get(section).iterator();
  }
  
  public int linesCount(String section)
  {
    return ini_file.get(section).size();
  }
  
  public String getStringKey(String line)
  {
	String key;
    int equalIndex = line.indexOf("=");
    if (equalIndex > 0)
      key = line.substring(0,equalIndex).trim();
    else
      key = line;
    return key;
  }
  
  public String getStringValue(String line)
  {
    String value = "";
    int equalIndex = line.indexOf("=");
    if (equalIndex > 0)
      value = line.substring(equalIndex+1).trim();
    return value;
  }
  
  // ����� ���������� ��������
  public String getValue(String section, String key)
  {
    String line;
    ArrayList<String> lines = ini_file.get(section);
    if (lines != null)
    {
      for(int i = 0; i < lines.size(); i++)
      {
        line = lines.get(i);
        if (line.startsWith(key+"="))
        {
          int equalIndex = line.indexOf("=");
          String value = line.substring(equalIndex+1).trim();
          return value; 
        }
      }
    }
    return "";
  }
  
  // ����� �������������� ��������
  public int getIntValue(String section, String key, int defValue)
  {
	int result = defValue;
	String value = getValue(section,key);
	if (!value.isEmpty())
	{
      try
      {
        result = Integer.valueOf(value);
      }
      catch (Exception E)
      {
        result = defValue;
      }
	}
	return result;
  }
  
  // ����� �������� boolean
  public boolean getBoolValue(String section, String key, boolean defValue)
  {
	boolean result = defValue;
    String value = getValue(section,key);
    if (!value.isEmpty())
    {
      if ((value.equals("1")) || (value.equalsIgnoreCase("true")))
        result = true;
      else if ((value.equals("0")) || (value.equalsIgnoreCase("false")))
        result = false;
    }
    return result;
  }
  
  // ����� �������� float
  public float getFloatValue(String section, String key, float defValue)
  {
	float result = defValue;
    String value = getValue(section,key);
    if (!value.isEmpty())
    {
      try
      {
        result = Float.valueOf(value);
      }
      catch (Exception E)
      {
        result = defValue;
      }
    }
    return result;
  }
  
  // ��������� ��������
  public void setValue(String section, String key, String value)
  {
	String set_line = key+"="+value;
    ArrayList<String> lines = ini_file.get(section);
    if (lines == null)
    {
      // ������ ���
      ini_file.put(section, new ArrayList<String>());	  
      ini_file.get(section).add(set_line);
    }
	else
    {
	  int index = -1;
	  String line;
      // ������ ����
      for(int i = 0; i < lines.size(); i++)
      {
        line = lines.get(i);
        if (line.startsWith(key+"="))
        {
          // ����� ����
          index = i;
          break;
        }
      }
      // ���� ���������� ?
      if (index >= 0)
        lines.set(index,set_line);
      else
    	lines.add(set_line);
    }
  }
  
  // ���������� ������
  public void addLine(String section, String line)
  {
	ArrayList<String> lines = ini_file.get(section);
	if (lines == null)
	  // ������ ���
	  ini_file.put(section, new ArrayList<String>());	  
	ini_file.get(section).add(line);
  }
  
  // ���������� � ����, �������� ����������� � ������ ������
  public void saveToFile(String fileName) throws IOException
  {
    BufferedWriter bw;
	bw = new BufferedWriter(new FileWriter(fileName));
    try 
    {
      Iterator<String> sections = enumSections();
  	  while (sections.hasNext()) 
  	  {
  		String line = sections.next();
  	    bw.write("["+line+"]");
  	    bw.newLine();
  	    Iterator<String> lines = enumLines(line);
  	    while (lines.hasNext()) 
  		{
  	      bw.write(lines.next());
  	      bw.newLine();
  		}
  	  }
    }
    finally
    {
      bw.close();
    }
  }
  
  // ����� ����������� ����� � Log
  public void LogProps(String TAG)
  {
    Iterator<String> sections = enumSections();
    while (sections.hasNext()) 
    {
      String line = sections.next();
      Log.d(TAG,"["+line+"]");
      Iterator<String> lines = enumLines(line);
      while (lines.hasNext()) 
        Log.d(TAG,lines.next());
    }
  }
  
  public float getAccuracy()
  {
    return 10;
  }

}
