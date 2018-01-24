// ISpeechUnderstander.aidl
package com.kandi.systemui;

import com.kandi.systemui.ITaskCallback;

interface IMobileSpeechClient {

    //开始识别
    void start(ITaskCallback callback);

    //停止识别
    void stop();

    //取消识别
    void cancel();


    boolean isTaskRunning();
    void stopRunningTask();
    void registerCallback(ITaskCallback cb);
    void unregisterCallback(ITaskCallback cb);
}
