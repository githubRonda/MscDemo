package com.ronda.mscdemo;

import android.app.Application;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.socks.library.KLog;

/**
 * Created by Ronda on 2018/1/23.
 */

public class MyApplication extends Application {

    private static MyApplication myApplication;
    @Override
    public void onCreate() {
        super.onCreate();

        myApplication = this;

        KLog.init(true, "Liu");

//        SpeechUtility speechUtility = SpeechUtility.createUtility(myApplication, "appid=5983e9bf");
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=5983e9bf");
    }

    public static MyApplication getMyApplication() {
        return myApplication;
    }
}
