package com.ronda.mscdemo.service;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.kandi.systemui.IMobileSpeechClient;
import com.kandi.systemui.ITaskCallback;

import com.ronda.mscdemo.utils.FucUtil;
import com.ronda.mscdemo.utils.JsonParser;
import com.socks.library.KLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;


/**
 * Created by Ronda on 2017/11/27.
 * <p>
 * 语音听写(词表)
 * 普通的语音识别, 添加了用户词表.
 * 在测试的时候, 发现这个可能会由于appid在多个APP中使用导致词表不生效的情况
 */

public class IatService extends Service {


    private static String TAG = IatService.class.getSimpleName();

    private static final String IS_UPLOAD_LEXICON = "is_upload_lexicon";

    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    private Toast mToast;
    private SharedPreferences mSharedPreferences;
    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;


    private boolean mTranslateEnable = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mStub;
    }


    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, null);

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(this, null);

        mSharedPreferences = getSharedPreferences("com.iflytek.setting", Activity.MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mIat) {
            // 退出时释放连接
            mIat.cancel();
            mIat.destroy();
        }
    }


    int ret = 0; // 函数调用返回值

    /**
     * 开始听写
     * 如何判断一次听写结束：OnResult isLast=true 或者 onError
     * @param callback
     */
    public void startListening(ITaskCallback callback) {

        if (callback == null){
            KLog.e("ITaskCallback is not allowed to be null when start");
            return;
        }

        // 若是正在会话中, 则判断优先级,设置是否取消会话
        if (mIat.isListening()) {
            try {
                if (callback.getPriority() > mCurrentCallback.getPriority()) {
                    mIat.cancel();
                    mCurrentCallback.onError("错误码:-2. 取消上一次识别会话, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority()); // 通知上一个会话. 跨进程调用肯定不属于同一个线程
                } else {
                    callback.onError("错误码:-3. 会话识别优先级不够, 该优先级为: " + callback.getPriority() + ", 而当前优先级为:" + mCurrentCallback.getPriority());// 通知新入的会话
                    return;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }


        if (!mSharedPreferences.getBoolean(IS_UPLOAD_LEXICON, false)) { // 若是没有上传词表, 则需要上传
            showTip("未构建词表, 识别率大大降低. 请先联网, 稍后再试");
            uploadUserwords();

            mRecognizerListener.onError(new SpeechError(-1, "未构建用户词表")); // 直接调用onError, 保证每种情况最终都会走到mRecognizerListener中
            return;
        }


        // 设置参数
        setParam();

        mCurrentCallback = callback;

        mIatResults.clear();

        boolean isShowDialog = false;
        if (isShowDialog) {
            // 显示听写对话框
            mIatDialog.setListener(mRecognizerDialogListener);
            mIatDialog.show();
            showTip("请开始说话…");
        } else {
            // 不显示听写对话框
            ret = mIat.startListening(mRecognizerListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("听写失败,错误码：" + ret);
            } else {
                showTip("请开始说话…");
            }
        }
    }

    public void stopListening() {
        mIat.stopListening();
        showTip("停止听写");
    }

    public void cancel() {
        mIat.cancel();
        showTip("取消听写");
    }

    public void uploadUserwords() {
        showTip("上传用户词表");
        String contents = FucUtil.readFile(this, "userwords", "utf-8");

        KLog.d("userwords: " + contents);

        // 指定引擎类型
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mIat.updateLexicon("userword", contents, mLexiconListener);
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
                mSharedPreferences.edit().putBoolean(IS_UPLOAD_LEXICON, true).commit();
            }
        }
    };

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            if (mTranslateEnable && error.getErrorCode() == 14002) {
                showTip(error.getPlainDescription(true) + "\n请确认是否已开通翻译功能");
            } else {
                showTip(error.getPlainDescription(true));
                doCallback(error.getPlainDescription(true), true);
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            if (mTranslateEnable) {
                printTransResult(results);
            } else {
                printResult(results);
            }

            if (isLast) {
                // TODO 最后的结果
                StringBuffer resultBuffer = new StringBuffer();
                for (String key : mIatResults.keySet()) {
                    resultBuffer.append(mIatResults.get(key));
                }

                KLog.d("resultBuffer.toString(): " + resultBuffer.toString());

                doCallback(resultBuffer.toString(), false);
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
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

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mIatResults.put(sn, text);

//        StringBuffer resultBuffer = new StringBuffer();
//        for (String key : mIatResults.keySet()) {
//            resultBuffer.append(mIatResults.get(key));
//        }
//
//        KLog.d("resultBuffer.toString(): " + resultBuffer.toString());
//
//        doCallback(resultBuffer.toString(), false);
    }

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            if (mTranslateEnable) {
                printTransResult(results);
            } else {
                printResult(results);
            }

        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            if (mTranslateEnable && error.getErrorCode() == 14002) {
                showTip(error.getPlainDescription(true) + "\n请确认是否已开通翻译功能");
            } else {
                showTip(error.getPlainDescription(true));
            }
        }

    };


    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    /**
     * 参数设置
     *
     * @return
     */
    public void setParam() {
        // 清空参数
        // mIat.setParameter(SpeechConstant.PARAMS, null);

        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

        this.mTranslateEnable = false;
        if (mTranslateEnable) {
            Log.i(TAG, "translate enable");
            mIat.setParameter(SpeechConstant.ASR_SCH, "1");
            mIat.setParameter(SpeechConstant.ADD_CAP, "translate");
            mIat.setParameter(SpeechConstant.TRS_SRC, "its");
        }

        String lag = "mandarin";
        if (lag.equals("en_us")) {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
            mIat.setParameter(SpeechConstant.ACCENT, null);

            if (mTranslateEnable) {
                mIat.setParameter(SpeechConstant.ORI_LANG, "en");
                mIat.setParameter(SpeechConstant.TRANS_LANG, "cn");
            }
        } else {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT, lag);

            if (mTranslateEnable) {
                mIat.setParameter(SpeechConstant.ORI_LANG, "cn");
                mIat.setParameter(SpeechConstant.TRANS_LANG, "en");
            }
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, "2000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, "300");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "0");

        // 设置音频来源
//        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "9");
//        // 设置采样频率
//        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "16000");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        // mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        // mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/iat.wav");
    }

    private void printTransResult(RecognizerResult results) {
        String trans = JsonParser.parseTransResult(results.getResultString(), "dst");
        String oris = JsonParser.parseTransResult(results.getResultString(), "src");

        if (TextUtils.isEmpty(trans) || TextUtils.isEmpty(oris)) {
            showTip("解析结果失败，请确认是否已开通翻译功能。");
        } else {
            KLog.d("原始语言:\n" + oris + "\n目标语言:\n" + trans);
        }
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

            IatService.this.startListening(callback);
        }

        @Override
        public void stop() throws RemoteException {
            IatService.this.stopListening();
        }

        @Override
        public void cancel() throws RemoteException {
            IatService.this.cancel();
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
