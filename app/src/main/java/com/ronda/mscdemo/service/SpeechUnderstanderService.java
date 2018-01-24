package com.ronda.mscdemo.service;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.kandi.systemui.IMobileSpeechClient;
import com.kandi.systemui.ITaskCallback;
import com.socks.library.KLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 语义识别(开发平台配置热词文件)
 * 优点: 可拓展性最高
 * <p>
 * 注意: 这个热词文件需要在讯飞开发者平台上配置. 同时还需要注意:SCENE 这个参数也需要同时更新
 */
public class SpeechUnderstanderService extends Service {


    private static String TAG = SpeechUnderstanderService.class.getSimpleName();
    public static final String errorTip = "请确认是否有在 aiui.xfyun.cn 配置语义。（另外，已开通语义，但从1115（含1115）以前的SDK更新到1116以上版本SDK后，语义需要重新到 aiui.xfyun.cn 配置）";

    // 语义理解对象（语音到语义）。
    private SpeechUnderstander mSpeechUnderstander;

    private Toast mToast;

    private Handler mHandler = new Handler();


    @Override
    public IBinder onBind(Intent intent) {
        return mStub;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSpeechUnderstander = SpeechUnderstander.createUnderstander(this, null);
        KLog.d("SpeechUnderstanderService --> onCreate, mSpeechUnderstander = " + mSpeechUnderstander);


        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mSpeechUnderstander) {
            // 退出时释放连接
            mSpeechUnderstander.cancel();
            mSpeechUnderstander.destroy();
        }
    }


    int ret = 0;// 函数调用返回值

    /**
     * 开始识别
     *
     * @param callback
     */
    public void startUnderstanding(ITaskCallback callback) {
        // mSpeechUnderstander.cancel();

        if (callback == null) {
            KLog.e("ITaskCallback is not allowed to be null when start");
            return;
        }

        KLog.d("startUnderstanding --> isUnderstanding: " + mSpeechUnderstander.isUnderstanding() + ", 传进来的: Callback: " + callback + ", binder: " + callback.asBinder());

        // 若是正在会话中, 则判断优先级,设置是否取消会话
        if (mSpeechUnderstander.isUnderstanding()) {
            try {
                KLog.w("callback.getPriority(): " + callback.getPriority() + ", mCurrentCallback.getPriority(): " + mCurrentCallback.getPriority());
                if (callback.getPriority() > mCurrentCallback.getPriority()) {
                    mSpeechUnderstander.cancel();
                    mCurrentCallback.onError("错误码:-2. 取消上一次识别会话, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority()); // 通知上一个会话. 跨进程调用肯定不属于同一个线程
                } else {
                    callback.onError("错误码:-3. 会话识别优先级不够, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority());// 通知新入的会话
                    return;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        setParam();

        mCurrentCallback = callback;
        ret = mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
        if (ret != 0) {
            showTip("语义理解失败,错误码:" + ret);
        } else {
            showTip("请开始说话…");
        }

    }

    /**
     * 停止识别
     */
    public void stopUnderstanding() {
        mSpeechUnderstander.stopUnderstanding();
        showTip("停止语义理解");
    }

    /**
     * 取消识别
     */

    public void cancel() {
        mSpeechUnderstander.cancel();
        showTip("取消语义理解");
    }


    /**
     * 语义理解回调。
     */
    private SpeechUnderstanderListener mSpeechUnderstanderListener = new SpeechUnderstanderListener() {

        @Override
        public void onResult(final UnderstanderResult result) {
            if (null != result) {
                Log.d(TAG, result.getResultString());

                // 显示
                String text = result.getResultString();
                if (!TextUtils.isEmpty(text)) {

                    KLog.d("onResult: ThreadId: " + Thread.currentThread().getId() + ", --> result: " + result);
                    parseTextResult(text);

                    if (0 != getResultError(text)) {
                        showTip(errorTip, Toast.LENGTH_LONG);
                    }
                }
            } else {
                showTip("识别结果不正确。");
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, data.length + "");
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            if (error.getErrorCode() == ErrorCode.MSP_ERROR_NO_DATA) {
                showTip(error.getPlainDescription(true), Toast.LENGTH_LONG);

                doCallback(error.getPlainDescription(true), true);
            } else {
                showTip(error.getPlainDescription(true) + ", " + errorTip, Toast.LENGTH_LONG);

                doCallback(error.getPlainDescription(true) + ", " + errorTip, true);
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    private void showTip(final String str) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    private void showTip(final String str, final int duration) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int lastDuration = mToast.getDuration();
                mToast.setText(str);
                mToast.setDuration(duration);
                mToast.show();
                mToast.setDuration(lastDuration);
            }
        });

    }

    private int getResultError(final String resultText) {
        int error = 0;
        try {
            final String KEY_ERROR = "error";
            final String KEY_CODE = "code";
            final JSONObject joResult = new JSONObject(resultText);
            final JSONObject joError = joResult.optJSONObject(KEY_ERROR);
            if (null != joError) {
                error = joError.optInt(KEY_CODE);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }//end of try-catch

        return error;
    }

    private void parseTextResult(String text) {
        try {
            JSONObject joResult = new JSONObject(text);

            String result = joResult.getString("text");
            if (!TextUtils.isEmpty(result)) {
                doCallback(result, false);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 参数设置
     *
     * @return
     */
    public void setParam() {
        /* mandarin(普通话),cantonese(粤语),en_us(英语)*/
        // 设置语言
        mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        mSpeechUnderstander.setParameter(SpeechConstant.ACCENT, "mandarin");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_BOS, "2000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_EOS, "300");

        // 设置标点符号，默认：1（有标点）
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_PTT, "0");

        // 设置音频来源
//        mSpeechUnderstander.setParameter(SpeechConstant.AUDIO_SOURCE, "9"); // 可以参考: MediaRecorder.AudioSource
        // 设置采样频率
//        mSpeechUnderstander.setParameter(SpeechConstant.SAMPLE_RATE, "8000"); // 默认 16000

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        // mSpeechUnderstander.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        //mSpeechUnderstander.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/sud.wav");
        // mSpeechUnderstander.setParameter(SpeechConstant.ASR_AUDIO_PATH, "/storage/extsd/msc/sud" + (index++) + ".wav");
        // KLog.d("音频保存目录: " + "/storage/extsd/msc/");

        // 设置语义情景
//        mSpeechUnderstander.setParameter(SpeechConstant.SCENE, "places");//取决于讯飞开发者平台上的配置
    }

    int index = 0;

    private ITaskCallback mCurrentCallback = null;

    private void doCallback(String result, boolean isError) {

        final int N = mCallbacks.beginBroadcast();

        KLog.d("doCallback: ThreadId: " + Thread.currentThread().getId() + ", result: " + result + ", Callback的数量: " + N);

        for (int i = 0; i < N; i++) {
            ITaskCallback taskCallback = mCallbacks.getBroadcastItem(i);

            try {
                KLog.w("N = " + N + ", 当前的Callback: " + mCurrentCallback + ", Binder: " + mCurrentCallback.asBinder() + ", getPriority " + mCurrentCallback.getPriority() +
                        "; 保存的: taskCallback: " + taskCallback + ", Binder: " + taskCallback.asBinder() + ", getPriority " + taskCallback.getPriority());
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            // 注意这里不能直接比较taskCallback, 必须要比较asBinder(). 原因: POI搜索界面测试发现每次传进来的taskCallback地址都不一样,但是asBinder() 却是一样的
            //if (taskCallback == mCurrentCallback) {
            if (taskCallback.asBinder() == mCurrentCallback.asBinder()) {
                try {
                    if (isError) {
                        taskCallback.onError(result);
                    } else {
                        taskCallback.onResult(result);
                    }
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }

                break;
            }
        }
        mCallbacks.finishBroadcast();
    }

    // 这里也可以使用 List 集合装载, 没发现和 RemoteCallbackList 有什么区别
    // 可能是多客户端时, RemoteCallbackList 装载的会在主线程中回调, 而List集合就不确定了
    final RemoteCallbackList<ITaskCallback> mCallbacks = new RemoteCallbackList<ITaskCallback>();

    //IPC调用是同步的。若一个IPC服务需要耗时操作的话，应该避免在UI线程中调用, 也就是IPC调用会挂起应用程序导致界面失去响应
    private final IMobileSpeechClient.Stub mStub = new IMobileSpeechClient.Stub() {

        // TODO: 2017/11/29 还可以再次优化, 每个动作都应该进行安全性校验判断

        @Override
        public void start(ITaskCallback callback) throws RemoteException {
            SpeechUnderstanderService.this.startUnderstanding(callback);
        }

        @Override
        public void stop() throws RemoteException {
            SpeechUnderstanderService.this.stopUnderstanding();
        }

        @Override
        public void cancel() throws RemoteException {
            SpeechUnderstanderService.this.cancel();
        }

        @Override
        public boolean isTaskRunning() throws RemoteException {
            return false;
        }

        @Override
        public void stopRunningTask() throws RemoteException {

        }

        @Override
        public void registerCallback(ITaskCallback cb) throws RemoteException {
            if (cb != null) {
                mCallbacks.register(cb);
            }
        }

        @Override
        public void unregisterCallback(ITaskCallback cb) throws RemoteException {
            if (cb != null) {
                mCallbacks.unregister(cb);
            }
        }
    };
}
