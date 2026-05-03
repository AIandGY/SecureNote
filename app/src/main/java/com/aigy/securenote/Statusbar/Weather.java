package com.aigy.securenote.Statusbar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.aigy.securenote.utils.PrefUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Weather {
    private static final String TAG = "Weather";
    
    // 个人版项目插件地址,请勿改动
    private static final String HOST = "https://pc78kyvcjx.re.qweatherapi.com/v7/weather/now?";
    
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final WeatherUpdateListener listener;

    public interface WeatherUpdateListener {
        void onWeatherUpdate(String weather, String temp);
        void onError(String message); // 错误回调
    }

    public Weather(Context context, WeatherUpdateListener listener) {
        this.context = context;
        this.listener = listener;
    }

    private final Runnable weatherUpdater = new Runnable() {
        @Override
        public void run() {
            updateLocationAndWeather();
            handler.postDelayed(this, 300000); 
        }
    };

    public void startAutoUpdate() {
        Log.d(TAG, "开启自动刷新...");
        handler.removeCallbacks(weatherUpdater);
        handler.post(weatherUpdater);
    }

    public void stopAutoUpdate() {
        handler.removeCallbacks(weatherUpdater);
    }

    private void updateLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && 
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fetchWeather("101010100");
            return;
        }
        tryNativeLocation();
    }

    private void tryNativeLocation() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            fetchWeather("101010100");
            return;
        }

        try {
            List<String> providers = locationManager.getProviders(true);
            Location bestLocation = null;
            for (String provider : providers) {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l == null) continue;
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    bestLocation = l;
                }
            }

            if (bestLocation != null) {
                processLocation(bestLocation);
            } else {
                fetchWeather("101010100");
            }
        } catch (SecurityException e) {
            fetchWeather("101010100");
        }
    }

    private void processLocation(Location location) {
        String locStr = String.format(java.util.Locale.US, "%.2f,%.2f", location.getLongitude(), location.getLatitude());
        fetchWeather(locStr);
    }

    private void fetchWeather(String location) {
        String apiKey = PrefUtils.getWeatherApiKey(context);
        
        // 1. 允许不填，但如果不填则通过回调提醒用户
        if (TextUtils.isEmpty(apiKey)) {
            notifyError("请检查天气API");
            return;
        }

        String url = HOST + "location=" + location + "&key=" + apiKey;
        
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                notifyError("网络错误");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    parseWeatherJson(body);
                } else {
                    // 2. 处理 API Key 无效或过期的 HTTP 错误
                    notifyError("请检查天气API");
                }
            }
        });
    }

    private void parseWeatherJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String code = root.optString("code");
            if ("200".equals(code)) {
                JSONObject now = root.getJSONObject("now");
                String weather = now.getString("text");
                String temp = now.getString("temp") + "°C";

                handler.post(() -> {
                    if (listener != null) listener.onWeatherUpdate(weather, temp);
                });
            } else if ("401".equals(code) || "403".equals(code) || "402".equals(code)) {
                // 3. 处理业务逻辑层面的 API 错误
                notifyError("请检查天气API");
            } else {
                notifyError("获取失败");
            }
        } catch (JSONException e) {
            notifyError("解析错误");
        }
    }

    private void notifyError(String msg) {
        handler.post(() -> {
            if (listener != null) listener.onError(msg);
        });
    }
}
