package org.servalproject;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.os.Environment;

/**
 * Created by Miga on 2018/02/04.
 * 如果ddms內的data打不開則需要到terminal輸入指令: adb shell su -c "chmod 777 /data"
 * 如果解除data資料夾內的檔案的權限,則需要輸入指令: adb shell su -c "chmod 777 /data/log file name.txt"
 * 如果要拉出data資料夾內的檔案到桌面,則需要輸入指令:adb pull /data/log file name.txt ~/桌面
 */
public class WriteLog
{
    public static void appendLog(String text)
    {
    	//File logFile = new File("/data/"+getDate()+".txt");
    	File logFile = new File("/data/"+getDate()+".txt");
    	
       // context.
        //File appDirectory = new File(  Environment.getDataDirectory() + "/ServalMeshLog" );
       // File logDirectory = new File( appDirectory + "/log" );
       // File logFile = new File( Environment.getDataDirectory().getAbsoluteFile(), "logcat" +getDate()+".txt" );
    	/* File appDirectory = new File( "sdcard/ServalMeshLog" );
         File logDirectory = new File( appDirectory + "/log" );
         File logFile = new File( logDirectory, "logcat" + getDate() + ".txt" );

         // create app folder
         if ( !appDirectory.exists() ) {
             appDirectory.mkdir();
         }

         // create log folder
         if ( !logDirectory.exists() ) {
             logDirectory.mkdir();
         }
  */
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(getDateTime()+"  "+text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    //取得現在時間
    public static String getDateTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }
    //取得現在時間
    public static String getDate(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }
}
