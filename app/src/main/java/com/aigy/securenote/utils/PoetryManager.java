package com.aigy.securenote.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PoetryManager {
    private static final String TAG = "PoetryManager";
    private static final String PREF_NAME = "poetry_prefs";
    private static final String KEY_TOKEN = "jinrishici_token";
    
    private static final String TOKEN_URL = "https://v2.jinrishici.com/token";
    private static final String SENTENCE_URL = "https://v2.jinrishici.com/sentence";

    private final OkHttpClient client = new OkHttpClient();
    private final SharedPreferences sp;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static final int MAX_RETRIES = 5;
    private int retryCount = 0;

    public interface PoetryCallback {
        void onSuccess(String content);
        void onError(String error);
    }

    public PoetryManager(Context context) {
        this.sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void getPoetry(PoetryCallback callback) {
        retryCount = 0; 
        String token = sp.getString(KEY_TOKEN, null);
        if (token == null) {
            fetchToken(callback);
        } else {
            fetchSentence(token, callback);
        }
    }

    private void fetchToken(PoetryCallback callback) {
        Request request = new Request.Builder().url(TOKEN_URL).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onError("获取Token失败"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if ("success".equals(jsonObject.getString("status"))) {
                            String token = jsonObject.getString("data");
                            sp.edit().putString(KEY_TOKEN, token).apply();
                            fetchSentence(token, callback);
                        }
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onError("解析Token异常"));
                    }
                }
            }
        });
    }

    private void fetchSentence(String token, PoetryCallback callback) {
        Request request = new Request.Builder()
                .url(SENTENCE_URL)
                .header("X-User-Token", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onError("获取诗词网络失败"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if ("success".equals(jsonObject.getString("status"))) {
                            JSONObject data = jsonObject.getJSONObject("data");
                            String content = data.getString("content");

                            // 记录每次获取到的古诗内容
                            Log.d(TAG, "第 " + (retryCount + 1) + " 次尝试获取诗句: [" + content + "] (长度: " + content.length() + ")");

                            // 检查长度是否超过16字
                            if (content.length() > 16 && retryCount < MAX_RETRIES) {
                                retryCount++;
                                fetchSentence(token, callback); // 递归重试
                            } else {
                                // 成功或达到最大重试次数，返回结果
                                mainHandler.post(() -> callback.onSuccess(content));
                            }
                        } else {
                            sp.edit().remove(KEY_TOKEN).apply();
                            mainHandler.post(() -> callback.onError("Token失效"));
                        }
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onError("解析诗词异常"));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("服务器响应错误: " + response.code()));
                }
            }
        });
    }
}
