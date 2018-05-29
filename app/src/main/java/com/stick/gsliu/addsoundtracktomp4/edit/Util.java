package com.stick.gsliu.addsoundtracktomp4.edit;

import android.os.Environment;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * Created by gsliu on 2018/3/6.
 */

public final class Util {
    public static final String TAG = "liugs";
    public static final String SDCARD_PATH = Environment.getExternalStorageDirectory().getPath();

    public static void closeFile( Closeable cloneable){
        try {
            cloneable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkNewFile(String filePath){
        File file = new File(filePath);
        if (file.exists()){
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkDeleteFile(String filePath){
        File file = new File(filePath);
        if (file.exists()){
            file.delete();
        }
    }
}
