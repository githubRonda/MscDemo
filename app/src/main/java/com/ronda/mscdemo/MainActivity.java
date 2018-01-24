package com.ronda.mscdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.kandi.systemui.IMobileSpeechClient;
import com.kandi.systemui.ITaskCallback;
import com.ronda.mscdemo.service.AsrService;
import com.ronda.mscdemo.service.IatService;
import com.ronda.mscdemo.service.SpeechUnderstanderService;
import com.ronda.mscdemo.service.TtsService;
import com.socks.library.KLog;

public class MainActivity extends AppCompatActivity {


    private IMobileSpeechClient mUnderstanderClient, mAsrClient, mIatClient, mTtsClient ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Intent understanderIntent = new Intent(this, SpeechUnderstanderService.class);
//        Intent asrIntent = new Intent(this, AsrService.class);
//        Intent iatIntent = new Intent(this, IatService.class);
//        Intent ttsIntent = new Intent(this, TtsService.class);
//
//        bindService(understanderIntent, understanderConn, Context.BIND_AUTO_CREATE);
//        bindService(asrIntent, asrConn, Context.BIND_AUTO_CREATE);
//        bindService(iatIntent, iatrConn, Context.BIND_AUTO_CREATE);
//        bindService(ttsIntent, ttsConn, Context.BIND_AUTO_CREATE);


//        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                try {
//                    mUnderstanderClient.start(null);
//                } catch (RemoteException e) {
//                    e.printStackTrace();
//                }
//            }
//        });

    }


    private ServiceConnection understanderConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mUnderstanderClient = IMobileSpeechClient.Stub.asInterface(service);
            try {
                KLog.e("命令词监听: 注册的callBack: " + mUnderstanderCallback + ", Binder: " +
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

    private ServiceConnection asrConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private ServiceConnection iatrConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private ServiceConnection ttsConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
}
