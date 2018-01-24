package com.iflytek.voicedemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;

import com.kandi.systemui.IMobileSpeechClient;
import com.kandi.systemui.ITaskCallback;
import com.socks.library.KLog;

/**
 * Created by Ronda on 2018/1/23.
 */

public class MainTestActivity extends Activity implements View.OnClickListener {

    private IMobileSpeechClient mUnderstanderClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_test);

        KLog.init(true, "Liu");

        bindUnderstanderService();

        findViewById(R.id.btn).setOnClickListener(this);
    }

    /**
     * 绑定语义识别服务
     */
    private void bindUnderstanderService() {
        Intent intent = new Intent("com.boe.action.UNDERSTANDER");
        intent.setPackage("com.ronda.mscdemo");
        bindService(intent, understanderConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onClick(View v) {
        try {
            KLog.w("客户端: Callback: " + mUnderstanderCallback + ", binder: " + mUnderstanderCallback.asBinder());
            mUnderstanderClient.start(mUnderstanderCallback);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private ServiceConnection understanderConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mUnderstanderClient = IMobileSpeechClient.Stub.asInterface(service);
            try {
                KLog.e("客户端注册的callBack: " + mUnderstanderCallback + ", Binder: " +
                        mUnderstanderCallback.asBinder() + ", getPriority " + mUnderstanderCallback.getPriority());
                mUnderstanderClient.registerCallback(mUnderstanderCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private ITaskCallback mUnderstanderCallback = new ITaskCallback.Stub() {

        @Override
        public void onResult(String result) throws RemoteException {
            KLog.e("ThreadId: " + Thread.currentThread().getId() + ", result: " + result);
        }

        @Override
        public void onError(String error) throws RemoteException {
            KLog.e("ThreadId: " + Thread.currentThread().getId() + ", error: " + error);
        }

        @Override
        public int getPriority() throws RemoteException {
            return 100; // 这里的优先级设置一个较高的值, 不让其他地方的发起的识别给中断了
        }
    };
}
