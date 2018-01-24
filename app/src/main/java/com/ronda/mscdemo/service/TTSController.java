package com.ronda.mscdemo.service;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;

import java.util.LinkedList;

/**
 * 当前的播报方式是队列模式。其原理就是依次将需要播报的语音放入链表中，播报过程是从头开始依次往后播报。
 * 用法:
 * TTSController tts = TTSController.getInstance(getApplicationContext());
 * tts.init();
 * tts.startSpeakingInQueue("你好啊");
 */
public class TTSController {

    /**
     * 请替换您自己申请的ID。
     */
    private final String appId = "57b3c4a9";

    public static TTSController ttsManager;
    private Context mContext;
    private SpeechSynthesizer mTts;
    private boolean isPlaying = false;
    private LinkedList<String> wordList = new LinkedList();
    private final int TTS_PLAY = 1;
    private final int CHECK_TTS_PLAY = 2;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TTS_PLAY:
                    synchronized (mTts) {
                        if (!isPlaying && mTts != null && wordList.size() > 0) {
                            isPlaying = true;
                            String playtts = wordList.removeFirst();
                            if (mTts == null) {
                                createSynthesizer();
                            }
                            mTts.startSpeaking(playtts, synthesizerListener);
                        }
                    }
                    break;
                case CHECK_TTS_PLAY:
                    if (!isPlaying) {
                        handler.obtainMessage(1).sendToTarget();
                    }
                    break;
            }
        }
    };

    private SynthesizerListener synthesizerListener = new SynthesizerListener() {

        @Override
        public void onCompleted(SpeechError speechError) {
            isPlaying = false;
            handler.obtainMessage(1).sendToTarget();
        }


        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {
            // 合成进度
            isPlaying = true;
        }

        @Override
        public void onSpeakBegin() {
            //开始播放
            isPlaying = true;
        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {
            //播放进度
            isPlaying = true;
        }

        @Override
        public void onSpeakResumed() {
            //继续播放
            isPlaying = true;
        }
    };

    private TTSController(Context context) {
        mContext = context.getApplicationContext();
        SpeechUtility.createUtility(mContext, SpeechConstant.APPID + "=" + appId);
        if (mTts == null) {
            createSynthesizer();
        }
    }

    private void createSynthesizer() {
        mTts = SpeechSynthesizer.createSynthesizer(mContext,
                new InitListener() {
                    @Override
                    public void onInit(int errorcode) {
                        if (ErrorCode.SUCCESS == errorcode) {
                        } else {
                            Toast.makeText(mContext, "语音合成初始化失败!", Toast.LENGTH_SHORT);
                        }
                    }
                });
    }


    public void init() {
        //设置发音人
        mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");
        //设置语速,值范围：[0, 100],默认值：50
        mTts.setParameter(SpeechConstant.SPEED, "55");
        //设置音量
        mTts.setParameter(SpeechConstant.VOLUME, "tts_volume");
        //设置语调
        mTts.setParameter(SpeechConstant.PITCH, "tts_pitch");
    }

    public static TTSController getInstance(Context context) {
        if (ttsManager == null) {
            ttsManager = new TTSController(context);
        }
        return ttsManager;
    }

    public void stopSpeaking() {
        if (wordList != null) {
            wordList.clear();
        }
        if (mTts != null) {
            mTts.stopSpeaking();
        }
        isPlaying = false;
    }

    public void destroy() {
        if (wordList != null) {
            wordList.clear();
        }
        if (mTts != null) {
            mTts.destroy();
        }
    }


    /**
     * 按照消息队列的形式一个一个进行语音播放.不会出现后后者强行中断前者语音播放的情况
     *
     * @param text
     */
    public void startSpeakingInQueue(String text) {
        if (wordList != null)
            wordList.addLast(text);
        handler.obtainMessage(CHECK_TTS_PLAY).sendToTarget();
    }
}
