package com.aigy.securenote.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.Database.NoteDao;
import com.aigy.securenote.R;
import com.aigy.securenote.utils.PrefUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 局域网 Web 同步服务端
 * 负责在局域网内建立 HTTP 服务，并动态维护连接状态通知
 */
public class LanServerService extends Service {

    private static final String TAG = "LanServerService";
    private static final int PORT = 8080;
    private static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_LAN_STATE_CHANGED = "com.aigy.securenote.LAN_STATE_CHANGED";

    private final IBinder binder = new LocalBinder();
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private NsdManager nsdManager;
    private WifiManager.MulticastLock multicastLock;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Map<String, ClientInfo> connectedClients = new ConcurrentHashMap<>();
    private final Map<String, String> sessionMap = new ConcurrentHashMap<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long serviceStartTime;

    public class LocalBinder extends Binder {
        public LanServerService getService() {
            return LanServerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        threadPool = Executors.newCachedThreadPool();

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("LanServerMulticastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
        }
        initNetworkListener();
    }

    private void initNetworkListener() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) { checkNetworkAndStopIfNeeded(); }
                @Override
                public void onLost(@NonNull Network network) { checkNetworkAndStopIfNeeded(); }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(), networkCallback);
        }
    }

    private void checkNetworkAndStopIfNeeded() {
        timerHandler.post(() -> {
            if (!PrefUtils.isCurrentNetworkTrusted(this)) {
                PrefUtils.setLanServiceEnabled(this, false);
                // 网络变更导致停止时发送广播刷新 UI
                sendBroadcast(new Intent(ACTION_LAN_STATE_CHANGED));
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!PrefUtils.isCurrentNetworkTrusted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, createNotification());
        if (!isRunning) {
            serviceStartTime = System.currentTimeMillis();
            startServer();
            registerMdns();
            sendBroadcast(new Intent(ACTION_LAN_STATE_CHANGED));
            startMaintenanceTask(); // 启动维护任务：清理过期连接及时长检查
        }
        return START_STICKY;
    }

    private synchronized void startServer() {
        isRunning = true;
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(PORT));
                while (isRunning) {
                    Socket client = serverSocket.accept();
                    threadPool.execute(() -> handleClientRequest(client));
                }
            } catch (Exception e) {
                isRunning = false;
            }
        });
    }

    private void handleClientRequest(Socket socket) {
        // 为 Socket 添加流量统计标记，解决 TrafficStats statsUid=-1 的调试日志问题
        try {
            TrafficStats.tagSocket(socket);
        } catch (Exception ignored) {
        }

        String clientIp = socket.getInetAddress().getHostAddress();

        if (PrefUtils.isLanWhitelistEnabled(this)) {
            List<String> whitelist = PrefUtils.getLanIpWhitelist(this);
            if (!whitelist.contains(clientIp)) {
                closeSocket(socket);
                return;
            }
        }

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {

            String firstLine = in.readLine();
            if (firstLine == null) return;
            String[] parts = firstLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String fullPath = parts[1];
            
            // 解析 Path 和 Query
            String path = fullPath;
            String query = "";
            if (fullPath.contains("?")) {
                int qIdx = fullPath.indexOf("?");
                path = fullPath.substring(0, qIdx);
                query = fullPath.substring(qIdx + 1);
            }

            int contentLength = 0;
            String userAgent = "Unknown";
            String deviceIdFromCookie = null;
            String header;

            while ((header = in.readLine()) != null && !header.isEmpty()) {
                String lower = header.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(header.substring(15).trim());
                } else if (lower.startsWith("user-agent:")) {
                    userAgent = header.substring(11).trim();
                } else if (lower.startsWith("cookie:")) {
                    String cookieContent = header.substring(7).trim();
                    String[] cookies = cookieContent.split(";");
                    for (String cookie : cookies) {
                        String[] pair = cookie.trim().split("=");
                        if (pair.length == 2 && "device_id".equals(pair[0])) {
                            deviceIdFromCookie = pair[1];
                        }
                    }
                }
            }

            if (isBlacklisted(clientIp, deviceIdFromCookie, userAgent)) {
                out.write("HTTP/1.1 404 Forbidden\r\n\r\n".getBytes());
                out.flush();
                return;
            }

            if (deviceIdFromCookie == null) {
                deviceIdFromCookie = UUID.randomUUID().toString();
            }

            String response;
            if (path.equals("/")) {
                response = buildResponse("200 OK", "text/html", loadHtmlFromAssets("login.html"), deviceIdFromCookie);
            } else if (path.equals("/index.html")) {
                response = buildResponse("200 OK", "text/html", loadHtmlFromAssets("index.html"), deviceIdFromCookie);
            } else if (path.equals("/api/auth/package")) {
                response = handleGetAuthPackage(deviceIdFromCookie);
            } else if (path.equals("/api/auth/verify") && "POST".equalsIgnoreCase(method)) {
                response = handleVerifyResponse(in, contentLength, clientIp, userAgent, deviceIdFromCookie);
            } else if (path.equals("/api/notes") && "GET".equalsIgnoreCase(method)) {
                // 每当网页端拉取数据，更新其在线状态
                recordClient(deviceIdFromCookie, clientIp, userAgent);
                response = handleGetNotes(deviceIdFromCookie);
            } else if (path.equals("/api/notes/update") && "POST".equalsIgnoreCase(method)) {
                response = handleUpdateNote(in, contentLength, deviceIdFromCookie);
            } else if (path.equals("/api/notes/add") && "POST".equalsIgnoreCase(method)) {
                response = handleAddNote(in, contentLength, deviceIdFromCookie);
            } else if (path.equals("/api/notes/delete") && ("DELETE".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))) {
                // 支持 DELETE 或 GET（带 query 参数）删除
                response = handleDeleteNote(query, deviceIdFromCookie);
            } else {
                response = buildResponse("404 Not Found", "text/plain", "Not Found", deviceIdFromCookie);
            }
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Request error: " + e.getMessage());
        }
    }

    private String handleDeleteNote(String query, String deviceId) {
        try {
            long id = -1;
            String[] params = query.split("&");
            for (String p : params) {
                String[] pair = p.split("=");
                if (pair.length == 2 && "id".equals(pair[0])) {
                    id = Long.parseLong(pair[1]);
                    break;
                }
            }
            if (id != -1) {
                AppDatabase.getDatabase(this).noteDao().deleteById(id);
                sendBroadcast(new Intent("com.aigy.securenote.NOTE_UPDATED"));
                return buildResponse("200 OK", "application/json", "{\"success\":true}", deviceId);
            }
            return buildResponse("400 Bad Request", "application/json", "{\"success\":false}", deviceId);
        } catch (Exception e) {
            return buildResponse("500 Error", "application/json", "{\"success\":false}", deviceId);
        }
    }

    private String handleUpdateNote(BufferedReader in, int length, String deviceId) throws IOException {
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            int r = in.read(body, read, length - read);
            if (r == -1) break;
            read += r;
        }
        try {
            JsonObject json = JsonParser.parseString(new String(body, 0, read)).getAsJsonObject();
            String ivBase64 = json.get("iv").getAsString();
            String dataBase64 = json.get("data").getAsString();

            String password = PrefUtils.getLanPassword(this);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(Base64.decode(ivBase64, Base64.NO_WRAP)));
            byte[] decrypted = cipher.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP));

            String noteJson = new String(decrypted, StandardCharsets.UTF_8);
            Note updatedNote = new Gson().fromJson(noteJson, Note.class);

            // 保存到数据库
            NoteDao dao = AppDatabase.getDatabase(this).noteDao();
            dao.update(updatedNote);

            // 发送广播通知 UI 更新
            sendBroadcast(new Intent("com.aigy.securenote.NOTE_UPDATED"));

            return buildResponse("200 OK", "application/json", "{\"success\":true}", deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Update note error: " + e.getMessage());
            return buildResponse("500 Error", "application/json", "{\"success\":false}", deviceId);
        }
    }

    private String handleAddNote(BufferedReader in, int length, String deviceId) throws IOException {
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            int r = in.read(body, read, length - read);
            if (r == -1) break;
            read += r;
        }
        try {
            JsonObject json = JsonParser.parseString(new String(body, 0, read)).getAsJsonObject();
            String ivBase64 = json.get("iv").getAsString();
            String dataBase64 = json.get("data").getAsString();

            String password = PrefUtils.getLanPassword(this);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(Base64.decode(ivBase64, Base64.NO_WRAP)));
            byte[] decrypted = cipher.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP));

            String noteJson = new String(decrypted, StandardCharsets.UTF_8);
            Note newNote = new Gson().fromJson(noteJson, Note.class);
            newNote.setId(0); // 确保 ID 为 0，Room 会自动生成

            // 保存到数据库
            NoteDao dao = AppDatabase.getDatabase(this).noteDao();
            dao.insert(newNote);

            // 发送广播通知 UI 更新
            sendBroadcast(new Intent("com.aigy.securenote.NOTE_UPDATED"));

            return buildResponse("200 OK", "application/json", "{\"success\":true}", deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Add note error: " + e.getMessage());
            return buildResponse("500 Error", "application/json", "{\"success\":false}", deviceId);
        }
    }

    private boolean isBlacklisted(String ip, String deviceId, String ua) {
        List<PrefUtils.BlacklistRule> blacklist = PrefUtils.getLanBlacklist(this);
        String os = parseOS(ua);
        String browser = parseBrowser(ua);

        for (PrefUtils.BlacklistRule rule : blacklist) {
            if (!rule.isEnabled) continue;
            if (rule.isFullMatch) {
                boolean matchesAll = true;
                int activeCriteria = 0;
                if (rule.blockIp) { activeCriteria++; if (!rule.ip.equals(ip)) matchesAll = false; }
                if (matchesAll && rule.blockDeviceId) { activeCriteria++; if (deviceId == null || !rule.deviceId.equals(deviceId)) matchesAll = false; }
                if (matchesAll && rule.blockOs) { activeCriteria++; if (!rule.os.equalsIgnoreCase(os)) matchesAll = false; }
                if (matchesAll && rule.blockBrowser) { activeCriteria++; if (!rule.browser.equalsIgnoreCase(browser)) matchesAll = false; }
                if (activeCriteria > 0 && matchesAll) return true;
            } else {
                if (rule.blockIp && rule.ip.equals(ip)) return true;
                if (rule.blockDeviceId && deviceId != null && rule.deviceId.equals(deviceId)) return true;
                if (rule.blockOs && rule.os.equalsIgnoreCase(os)) return true;
                if (rule.blockBrowser && rule.browser.equalsIgnoreCase(browser)) return true;
            }
        }
        return false;
    }

    private void closeSocket(Socket socket) {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private String handleGetNotes(String deviceId) {
        try {
            List<Note> notes = AppDatabase.getDatabase(this).noteDao().getAllNotesSync();
            String json = new Gson().toJson(notes);
            String password = PrefUtils.getLanPassword(this);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            JsonObject resPkg = new JsonObject();
            resPkg.addProperty("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            resPkg.addProperty("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            return buildResponse("200 OK", "application/json", resPkg.toString(), deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Encrypt notes error: " + e.getMessage());
            return buildResponse("500 Error", "application/json", "{}", deviceId);
        }
    }

    private String handleGetAuthPackage(String deviceId) {
        try {
            String password = PrefUtils.getLanPassword(this);
            String challenge = UUID.randomUUID().toString().substring(0, 8);
            String sid = UUID.randomUUID().toString();
            sessionMap.put(sid, challenge);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(challenge.getBytes(StandardCharsets.UTF_8));
            JsonObject json = new JsonObject();
            json.addProperty("sid", sid);
            json.addProperty("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            json.addProperty("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            return buildResponse("200 OK", "application/json", json.toString(), deviceId);
        } catch (Exception e) {
            return buildResponse("500 Error", "application/json", "{}", deviceId);
        }
    }

    private String handleVerifyResponse(BufferedReader in, int length, String ip, String userAgent, String deviceId) throws IOException {
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            int r = in.read(body, read, length - read);
            if (r == -1) break;
            read += r;
        }
        try {
            JsonObject json = JsonParser.parseString(new String(body, 0, read)).getAsJsonObject();
            String sid = json.get("sid").getAsString();
            String decrypted = json.get("decrypted").getAsString();
            String challenge = sessionMap.remove(sid);
            boolean success = challenge != null && challenge.equals(decrypted);
            if (success) recordClient(deviceId, ip, userAgent);
            JsonObject res = new JsonObject();
            res.addProperty("success", success);
            if (success) res.addProperty("token", UUID.randomUUID().toString());
            return buildResponse("200 OK", "application/json", res.toString(), deviceId);
        } catch (Exception e) {
            return buildResponse("500 Error", "application/json", "{\"success\":false}", deviceId);
        }
    }

    /**
     * 记录或更新已验证连接的客户端
     */
    private void recordClient(String deviceId, String ip, String ua) {
        // 检查是否为新增客户端，避免重复刷新导致的通知频繁更新
        boolean isNewClient = !connectedClients.containsKey(deviceId);

        ClientInfo info = connectedClients.get(deviceId);
        if (info == null) {
            info = new ClientInfo();
            info.deviceId = deviceId;
            info.connectTime = System.currentTimeMillis();
        }
        info.ip = ip;
        info.lastActiveTime = System.currentTimeMillis(); // 关键：更新活跃时间戳
        info.os = parseOS(ua);
        info.browser = parseBrowser(ua);
        String shortId = deviceId.length() > 4 ? deviceId.substring(0, 4) : deviceId;
        info.deviceName = info.os + " 设备 [" + shortId + "]";
        connectedClients.put(deviceId, info);

        // 仅在有新设备连接时更新通知，修复亮屏刷新导致 SystemUI 报 fullscreeenIntent==null 的错误
        if (isNewClient) {
            updateNotification();
        }
    }

    private String parseOS(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "未知系统";
    }

    private String parseBrowser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return "Safari";
        return "其他浏览器";
    }

    public List<ClientInfo> getConnectedClients() {
        return new ArrayList<>(connectedClients.values());
    }

    /**
     * 断开指定设备连接，并更新通知状态
     */
    public void disconnectClient(String deviceId) {
        connectedClients.remove(deviceId);
        updateNotification();
    }

    private String buildResponse(String status, String type, String content, String deviceId) {
        return "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + type + "; charset=utf-8\r\n" +
                "Content-Length: " + content.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Set-Cookie: device_id=" + deviceId + "; Path=/; Max-Age=31536000\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n" + content;
    }

    private String loadHtmlFromAssets(String name) {
        try (InputStream is = getAssets().open("html/" + name);
             BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
            return sb.toString();
        } catch (Exception e) { return "File not found"; }
    }

    private void registerMdns() {
        String prefix = PrefUtils.getLanAddressPrefix(this);
        if (prefix.isEmpty()) prefix = Build.MODEL.replaceAll("[^a-zA-Z0-9]", "");
        String serviceName = "SecureNote-" + prefix;
        threadPool.execute(() -> {
            try {
                NsdServiceInfo serviceInfo = new NsdServiceInfo();
                serviceInfo.setServiceName(serviceName);
                serviceInfo.setServiceType("_http._tcp.");
                serviceInfo.setPort(PORT);
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                    @Override public void onServiceRegistered(NsdServiceInfo s) {}
                    @Override public void onRegistrationFailed(NsdServiceInfo s, int e) {}
                    @Override public void onServiceUnregistered(NsdServiceInfo s) {}
                    @Override public void onUnregistrationFailed(NsdServiceInfo s, int e) {}
                });
            } catch (Exception ignored) {}
        });
    }

    /**
     * 启动维护任务，负责清理过期连接及自动关停逻辑
     */
    private void startMaintenanceTask() {
        timerHandler.removeCallbacks(maintenanceRunnable);
        timerHandler.postDelayed(maintenanceRunnable, 30000);
    }

    private final Runnable maintenanceRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            boolean changed = false;

            // 1. 清理超过 30 秒无任何请求的设备
            for (Map.Entry<String, ClientInfo> entry : connectedClients.entrySet()) {
                if (now - entry.getValue().lastActiveTime > 30000) {
                    connectedClients.remove(entry.getKey());
                    changed = true;
                }
            }

            if (changed) {
                updateNotification();
                sendBroadcast(new Intent(ACTION_LAN_STATE_CHANGED));
            }

            // 2. 自动停止检查：最大运行时间
            int maxHours = PrefUtils.getLanMaxRunTime(LanServerService.this);
            if (now - serviceStartTime >= maxHours * 3600000L) {
                Log.i(TAG, "已达到最大运行时间 (" + maxHours + " 小时)，自动关闭服务");
                PrefUtils.setLanServiceEnabled(LanServerService.this, false);
                // 关键点：超时自动关闭时，发送广播刷新 UI
                sendBroadcast(new Intent(ACTION_LAN_STATE_CHANGED));
                stopSelf();
                return;
            }

            // 3. 闲置自动停止：如果没有活跃设备连接，且超过 30 分钟无活动，则关闭服务 (按需开启)
            // if (connectedClients.isEmpty() && (now - serviceStartTime > 30 * 60000L)) { ... }

            if (isRunning) {
                timerHandler.postDelayed(this, 30000);
            }
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("lan_server", "局域网同步服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /**
     * 根据当前连接状态创建前台通知内容
     */
    private Notification createNotification() {
        int count = connectedClients.size();
        String contentText = (count == 0) ? "正在等待连接..." : count + " 台设备已连接";

        return new NotificationCompat.Builder(this, "lan_server")
                .setContentTitle("网页同步已开启")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_menu_password)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * 实时刷新前台通知
     */
    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
        timerHandler.removeCallbacksAndMessages(null);
        // 服务销毁时统一发送广播，确保主界面图标同步消失
        sendBroadcast(new Intent(ACTION_LAN_STATE_CHANGED));
        super.onDestroy();
    }

    public static class ClientInfo {
        public String deviceId;
        public String deviceName;
        public String ip;
        public long connectTime;
        public long lastActiveTime; // 记录设备最后一次请求 App 的时间
        public String os;
        public String browser;
        public String getDuration() {
            long sec = (System.currentTimeMillis() - connectTime) / 1000;
            return (sec / 60) + "分" + (sec % 60) + "秒";
        }
    }
}
