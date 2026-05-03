package com.aigy.securenote.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.baidu.aip.asrwakeup3.core.recog.MyRecognizer;
import com.baidu.aip.asrwakeup3.core.recog.RecogResult;
import com.baidu.aip.asrwakeup3.core.recog.listener.StatusRecogListener;
import com.baidu.speech.asr.SpeechConstant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音识别助手 - 智能指令检测版
 * 逻辑：3秒无有效文字内容即自动结束，过滤背景噪音（不依赖音量回调）
 */
public class SpeechHelper {
    private static final String TAG = "SpeechHelper";
    private final MyRecognizer myRecognizer;
    private final SpeechCallback callback;
    private final Context context;

    // 自动结束控制：3秒无内容检测
    private final Handler silenceHandler = new Handler(Looper.getMainLooper());
    private static final long SILENCE_TIMEOUT = 3000; // 3秒
    
    private final Runnable silenceRunnable = () -> {
        Log.d(TAG, "3秒未检测到有效语音指令，自动停止识别");
        stopListening();
    };

    public interface SpeechCallback {
        void onResult(String text);
        void onError(String error);
    }

    public SpeechHelper(Context context, SpeechCallback callback) {
        this.context = context;
        this.callback = callback;

        StatusRecogListener listener = new StatusRecogListener() {
            @Override
            public void onAsrBegin() {
                super.onAsrBegin();
                // 开启识别后启动初始计时
                resetSilenceTimer();
            }

            @Override
            public void onAsrPartialResult(String[] results, RecogResult recogResult) {
                super.onAsrPartialResult(results, recogResult);
                // 只有当百度SDK识别出具体的文字片段时，才认为用户在有效表达，重置3秒计时
                // 噪音（如风声、杂音）不会被识别为文字，故不会重置计时，能正常结束
                if (results != null && results.length > 0 && !results[0].isEmpty()) {
                    resetSilenceTimer();
                }
            }

            @Override
            public void onAsrVolume(int volumePercent, int volume) {
                // 不再根据音量重置计时，彻底解决噪音导致不结束的问题
            }

            @Override
            public void onAsrFinalResult(String[] results, RecogResult recogResult) {
                super.onAsrFinalResult(results, recogResult);
                cancelSilenceTimer();
                if (results != null && results.length > 0) {
                    callback.onResult(results[0]);
                }
            }

            @Override
            public void onAsrFinishError(int errorCode, int subErrorCode, String descMessage, RecogResult recogResult) {
                super.onAsrFinishError(errorCode, subErrorCode, descMessage, recogResult);
                cancelSilenceTimer();
                
                //错误码
                if (errorCode == 7 || subErrorCode == 7001) {
                    callback.onError("我没听清，可以再说一次吗？");
                } else if (errorCode == 2 || subErrorCode == 2100) {
                    callback.onError("网络状态不佳，请检查网络");
                } else if (errorCode == 4 || subErrorCode == 4004) {
                    callback.onError("请检查百度语音识别API配置是否正确");
                } else if (errorCode != 0) {
                    callback.onError("识别失败: " + descMessage);
                }
            }

            @Override
            public void onAsrExit() {
                super.onAsrExit();
                cancelSilenceTimer();
            }
        };

        this.myRecognizer = new MyRecognizer(context, listener);
    }

    private void resetSilenceTimer() {
        silenceHandler.removeCallbacks(silenceRunnable);
        silenceHandler.postDelayed(silenceRunnable, SILENCE_TIMEOUT);
    }

    private void cancelSilenceTimer() {
        silenceHandler.removeCallbacks(silenceRunnable);
    }

    public void startListening() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(SpeechConstant.APP_ID, PrefUtils.getAsrAppId(context));
        params.put(SpeechConstant.APP_KEY, PrefUtils.getAsrApiKey(context));
        params.put(SpeechConstant.SECRET, PrefUtils.getAsrSecretKey(context));

        params.put(SpeechConstant.VAD, "touch"); 
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false); // 节省资源，不处理噪音音量
        params.put(SpeechConstant.DISABLE_PUNCTUATION, false);

        if (myRecognizer != null) {
            myRecognizer.start(params);
            resetSilenceTimer(); // 启动后开始倒计时
        }
    }

    public void stopListening() {
        cancelSilenceTimer();
        if (myRecognizer != null) {
            myRecognizer.stop();
        }
    }

    public void destroy() {
        cancelSilenceTimer();
        if (myRecognizer != null) {
            myRecognizer.release();
        }
    }
}
