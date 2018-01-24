package com.ronda.mscdemo.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.kandi.systemui.IMobileSpeechClient;
import com.kandi.systemui.ITaskCallback;

import com.ronda.mscdemo.utils.FucUtil;
import com.ronda.mscdemo.utils.JsonParser;
import com.socks.library.KLog;


/**
 * Created by Ronda on 2017/11/27.
 * <p>
 * 语法识别(命令词, 本地语法文件)
 * <p>
 * 优点: 在命令词正确的情况下, 识别率最高
 * 缺点: 由于这个语法命令词识别只有两种结果: 在命令词列表中选择一个匹配度最高的, 或者 没有匹配项.
 * 也正是由于会优先在命令词列表中查找一个匹配度最高的, 所以当说出的一个命令词不在命令词列表中的话,但有几个字是相近的话, 就会识别错乱
 */

public class AsrService extends Service {


    private static String TAG = AsrService.class.getSimpleName();
    // 语音识别对象
    private SpeechRecognizer mAsr;
    private Toast mToast;
    // 缓存
    private SharedPreferences mSharedPreferences;
    // 云端语法文件
    private String mCloudGrammar = null;

    private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private static final String GRAMMAR_TYPE_ABNF = "abnf";

    private static final String IS_UPLOAD_LEXICON = "is_upload_lexicon";

    private String mEngineType = SpeechConstant.TYPE_CLOUD; //选择云端or本地

    private Handler mHandler = new Handler();


    @Override
    public IBinder onBind(Intent intent) {
        return mStub;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(this, null);
        mCloudGrammar = FucUtil.readFile(this, "grammar_sample.abnf", "utf-8");

        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mAsr) {
            // 退出时释放连接
            mAsr.cancel();
            mAsr.destroy();
        }
    }


    // 语法、词典临时变量
    String mContent;
    // 函数调用返回值
    int ret = 0;


    private void cancel() {
        mAsr.cancel();
        showTip("取消识别");
    }

    private void stopListening() {
        mAsr.stopListening();
        showTip("停止识别");
    }


    private void startListening(ITaskCallback callback) {

        if (callback == null){
            KLog.e("ITaskCallback is not allowed to be null when start");
            return;
        }

        // 若是正在会话中, 则判断优先级,设置是否取消会话
        if (mAsr.isListening()){
            try {
                if (callback.getPriority() > mCurrentCallback.getPriority()) {
                    mAsr.cancel();
                    mCurrentCallback.onError("错误码:-2. 取消上一次识别会话, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority()); // 通知上一个会话. 跨进程调用肯定不属于同一个线程
                } else {
                    callback.onError("错误码:-3. 会话识别优先级不够, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority());// 通知新入的会话
                    return;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        // 设置参数
        if (!setParam()) {
            showTip("未构建语法, 识别率大大降低. 请先联网, 稍后再试");
            uploadGrammar();

            mRecognizerListener.onError(new SpeechError(-1, "未构建语法")); // 直接调用onError, 保证每种情况最终都会走到mRecognizerListener中
            return;
        }

        mCurrentCallback = callback;

        ret = mAsr.startListening(mRecognizerListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("识别失败,错误码: " + ret);
        }
    }

    private void uploadGrammar() {
        showTip("上传预设关键词/语法文件");
        // 在线-构建语法文件，生成语法id
        Log.d("Liu", "grammar: " + mCloudGrammar);
        mContent = new String(mCloudGrammar);
        //指定引擎类型
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mAsr.buildGrammar(GRAMMAR_TYPE_ABNF, mContent, mCloudGrammarListener);
        if (ret != ErrorCode.SUCCESS)
            showTip("语法构建失败,错误码：" + ret);
    }


    /**
     * 云端构建语法监听器。
     */
    private GrammarListener mCloudGrammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                String grammarID = new String(grammarId);
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                if (!TextUtils.isEmpty(grammarId))
                    editor.putString(KEY_GRAMMAR_ABNF_ID, grammarID);
                editor.commit();
                showTip("语法构建成功：" + grammarId);
            } else {
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };


    private void uploadUserwords() {
        // 上传用户词表
        String contents = FucUtil.readFile(this, "userwords", "utf-8");
        Log.d("Liu", "userwords: " + contents);
        // 指定引擎类型
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mAsr.updateLexicon("userword", contents, mLexiconListener);
        if (ret != ErrorCode.SUCCESS)
            showTip("上传热词失败,错误码：" + ret);
    }

    /**
     * 上传联系人/词表监听器。
     */
    private LexiconListener mLexiconListener = new LexiconListener() {

        @Override
        public void onLexiconUpdated(String lexiconId, SpeechError error) {
            if (error != null) {
                showTip(error.toString());
            } else {
                showTip("上传成功");
                mSharedPreferences.edit().putBoolean(IS_UPLOAD_LEXICON, true);
            }
        }
    };


    /**
     * 识别监听器。
     */
    private boolean isRecognizing = false; //是否正在进行识别. 每次识别可能会回调多次 onResult()
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result) {
                Log.d(TAG, "recognizer result：" + result.getResultString());

                if (!isRecognizing) {
                    String text;
                    if ("cloud".equalsIgnoreCase(mEngineType)) {
                        text = JsonParser.parseGrammarResult(result.getResultString());
                    } else {
                        text = JsonParser.parseLocalGrammarResult(result.getResultString());
                    }

                    // 显示
                    KLog.d("AsrService --> onResult: " + result);

                    doCallback(text, false);
                }

                if (isLast) {
                    isRecognizing = false; // 说明本次识别已结束
                }

            } else {
                Log.d(TAG, "recognizer result : null");
            }
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
            showTip("ErrorCode" + error.getErrorCode() + ", " + error.getPlainDescription(true));
            doCallback("ErrorCode" + error.getErrorCode() + ", " + error.getPlainDescription(true), true);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
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

    /**
     * 参数设置
     *
     * @return
     */
    public boolean setParam() {
        boolean result = false;
        //设置识别引擎
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        //设置返回结果为json格式
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");


        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mAsr.setParameter(SpeechConstant.VAD_BOS, "2000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mAsr.setParameter(SpeechConstant.VAD_EOS, "300");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mAsr.setParameter(SpeechConstant.ASR_PTT, "0");

        // 设置音频来源
//        mAsr.setParameter(SpeechConstant.AUDIO_SOURCE, "9");
//        // 设置采样频率
//        mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "16000");


        if ("cloud".equalsIgnoreCase(mEngineType)) {
            String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
            if (TextUtils.isEmpty(grammarId)) {
                result = false;
            } else {
                //设置云端识别使用的语法id
                mAsr.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
                result = true;
            }
        }

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        //mAsr.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        //mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/asr.wav");
        return result;
    }


    private ITaskCallback mCurrentCallback = null;
    private void doCallback(String result, boolean isError) {

        final int N = mCallbacks.beginBroadcast();

        for (int i = 0; i < N; i++) {
            ITaskCallback taskCallback = mCallbacks.getBroadcastItem(i);

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

    final RemoteCallbackList<ITaskCallback> mCallbacks = new RemoteCallbackList<ITaskCallback>(); // 这里也可以使用 List 集合, 没发现和 RemoteCallbackList 有什么区别


    //IPC调用是同步的。若一个IPC服务需要耗时操作的话，应该避免在UI线程中调用, 也就是IPC调用会挂起应用程序导致界面失去响应
    private final IMobileSpeechClient.Stub mStub = new IMobileSpeechClient.Stub() {

        @Override
        public void start(ITaskCallback callback) throws RemoteException {

            AsrService.this.startListening(callback);
        }

        @Override
        public void stop() throws RemoteException {
            AsrService.this.stopListening();
        }

        @Override
        public void cancel() throws RemoteException {
            AsrService.this.cancel();
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
