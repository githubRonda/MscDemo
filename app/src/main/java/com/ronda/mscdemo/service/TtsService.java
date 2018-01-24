package com.ronda.mscdemo.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.ronda.mscdemo.R;

/**
 * Created by Ronda on 2018/1/10.
 *
 * 语音合成(强行中断前者型)
 * 1. 在语音合成中, 发音的语种,方言 和 发音人 都是通过 设置 VOICE_NAME 来完成的. 而在语音/语义识别中, 则要先设置 LANGUAGE 和 ACCENT才可以"听"懂你说的话.
 * 2. 语音合成中一般是不需要回调的, 只需要传入一段文本即可
 * 3. 当前这个 TtsService 的功能设计是, 若当前正在进行发音还没结束的话, 会被后面的识别给终止掉. 若想让识别像导航播放一样,则需要高德demo中的播放代码
 */

public class TtsService extends Service {

    private static String TAG = TtsService.class.getSimpleName();
    // 语音合成对象
    private SpeechSynthesizer mTts;

    private String[] mCloudVoicersEntries;
    private String[] mCloudVoicersValue;

    // 默认发音人
    private String voicer = "xiaoyan";

    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;

    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;

    private Toast mToast;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mTts = SpeechSynthesizer.createSynthesizer(TtsService.this, null);

        // 云端发音人名称列表(这两个数组必须是对应的)
        mCloudVoicersEntries = getResources().getStringArray(R.array.voicer_cloud_entries);
        mCloudVoicersValue = getResources().getStringArray(R.array.voicer_cloud_values);

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mTts) {
            // 退出时释放连接
            mTts.stopSpeaking();
            mTts.destroy();
        }
    }

    public void startSpeaking(String text) {
        if (null == mTts) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            this.showTip("创建对象失败，请确认 libmsc.so 放置正确，且有调用 createUtility 进行初始化");
            return;
        }

        if (mTts.isSpeaking()){
            mTts.stopSpeaking();
        }

        // 设置参数
        setParam();
        int code = mTts.startSpeaking(text, mTtsListener);

//        /**
//         * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//         * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//         */
//        String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//        int code = mTts.synthesizeToUri(text, path, mTtsListener);

        if (code != ErrorCode.SUCCESS) {
            showTip("语音合成失败,错误码: " + code);
        }
    }

    // 取消合成
    public void stopSpeaking() {
        mTts.stopSpeaking();
    }

    // 暂停播放
    public void pauseSpeaking() {
        mTts.pauseSpeaking();
    }

    // 继续播放
    public void resumeSpeaking() {
        mTts.resumeSpeaking();
    }


    /**
     * 合成回调监听。
     */
    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            showTip("开始播放");
        }

        @Override
        public void onSpeakPaused() {
            showTip("暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            showTip("继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos, String info) {
            // 合成进度
            mPercentForBuffering = percent;
            showTip(String.format(getString(R.string.tts_toast_format), mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
            showTip(String.format(getString(R.string.tts_toast_format), mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                showTip("播放完成");
            } else if (error != null) {
                showTip(error.getPlainDescription(true));
            }
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


    /**
     * 参数设置
     *
     * @return
     */
    private void setParam() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        // 根据合成引擎设置相应参数
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            // 设置在线合成发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
            //设置合成语速
            mTts.setParameter(SpeechConstant.SPEED, "50");
            //设置合成音调
            mTts.setParameter(SpeechConstant.PITCH, "50");
            //设置合成音量
            mTts.setParameter(SpeechConstant.VOLUME, "50");
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            // 设置本地合成发音人 voicer为空，默认通过语记界面指定发音人。
            mTts.setParameter(SpeechConstant.VOICE_NAME, "");
            /**
             * TODO 本地合成不设置语速、音调、音量，默认使用语记设置
             * 开发者如需自定义参数，请参考在线合成参数设置
             */
        }

        //设置播放器音频流类型. 通话:0; 系统:1; 铃声:2; 音乐:3; 闹铃:4; 通知:5
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        // mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        // mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");
    }

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
}
