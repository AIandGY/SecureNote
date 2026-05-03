package com.aigy.securenote;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.Database.PasswordEntry;
import com.aigy.securenote.databinding.ActivityDataManagementBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数据管理页面
 * 负责笔记数据、密码数据、API配置的导出与导入（JSON格式）
 */
public class DataManagementActivity extends AppCompatActivity {

    private static final String TAG = "DataManagementActivity";
    private ActivityDataManagementBinding binding;
    private AppDatabase db;
    private Gson gson;

    // 笔记导出启动器
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportNotesToJson(uri);
                }
            });

    // 笔记导入启动器
    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importNotesFromJson(uri);
                }
            });

    // 密码导出启动器
    private final ActivityResultLauncher<Intent> exportPasswordsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportPasswordsToJson(uri);
                }
            });

    // 密码导入启动器
    private final ActivityResultLauncher<Intent> importPasswordsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importPasswordsFromJson(uri);
                }
            });

    // API 配置导出启动器
    private final ActivityResultLauncher<Intent> exportApiLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportApiSettings(uri);
                }
            });

    // API 配置导入启动器
    private final ActivityResultLauncher<Intent> importApiLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importApiSettings(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDataManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getDatabase(this);
        gson = new Gson();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCustomThemeColor();
    }

    private void applyCustomThemeColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultPrimary = typedValue.data;

        int textColor = PrefUtils.getTextMainColor(this, defaultPrimary);
        int iconColor = PrefUtils.getIconMainColor(this, defaultPrimary);

        binding.headerNotesBackup.setTextColor(textColor);
        binding.headerPasswordsBackup.setTextColor(textColor);
        binding.headerApiBackup.setTextColor(textColor);

        binding.ivExport.setColorFilter(iconColor);
        binding.ivImport.setColorFilter(iconColor);
        binding.ivExportPasswords.setColorFilter(iconColor);
        binding.ivImportPasswords.setColorFilter(iconColor);
        binding.ivExportApi.setColorFilter(iconColor);
        binding.ivImportApi.setColorFilter(iconColor);
    }

    private void initUI() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 笔记导出/导入
        binding.itemExport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            String fileName = "Notes_Backup_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".json";
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            exportLauncher.launch(intent);
        });

        binding.itemImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importLauncher.launch(intent);
        });

        // 密码导出/导入
        binding.itemExportPasswords.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            String fileName = "Passwords_Backup_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".json";
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            exportPasswordsLauncher.launch(intent);
        });

        binding.itemImportPasswords.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importPasswordsLauncher.launch(intent);
        });

        // API 配置导出/导入
        binding.itemExportApi.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            String fileName = "API_Config_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".json";
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            exportApiLauncher.launch(intent);
        });

        binding.itemImportApi.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importApiLauncher.launch(intent);
        });
    }

    private void exportNotesToJson(Uri uri) {
        new Thread(() -> {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                List<Note> notes = db.noteDao().getAllNotesSync();
                String json = gson.toJson(notes);
                outputStream.write(json.getBytes());
                showToast("笔记导出成功: " + notes.size() + " 条");
            } catch (Exception e) {
                Log.e(TAG, "导出笔记错误", e);
                showToast("导出失败");
            }
        }).start();
    }

    private void importNotesFromJson(Uri uri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Type listType = new TypeToken<List<Note>>(){}.getType();
                List<Note> notes = gson.fromJson(sb.toString(), listType);
                if (notes != null) {
                    int importedCount = 0;
                    int skippedCount = 0;
                    for (Note n : notes) {
                        // 查重：标题、内容、时间戳一致则跳过
                        Note existing = db.noteDao().findNote(n.getTitle(), n.getContent(), n.getTimestamp());
                        if (existing == null) {
                            n.setId(0); 
                            db.noteDao().insert(n);
                            importedCount++;
                        } else {
                            skippedCount++;
                        }
                    }
                    showToast("导入完成: 新增 " + importedCount + " 条，跳过重复 " + skippedCount + " 条");
                }
            } catch (Exception e) {
                Log.e(TAG, "导入笔记错误", e);
                showToast("导入失败: 格式错误");
            }
        }).start();
    }

    private void exportPasswordsToJson(Uri uri) {
        new Thread(() -> {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                List<PasswordEntry> passwords = db.passwordDao().getAllPasswordsSync();
                String json = gson.toJson(passwords);
                outputStream.write(json.getBytes());
                showToast("密码数据导出成功: " + passwords.size() + " 条");
            } catch (Exception e) {
                Log.e(TAG, "导出密码失败", e);
                showToast("导出失败");
            }
        }).start();
    }

    private void importPasswordsFromJson(Uri uri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Type listType = new TypeToken<List<PasswordEntry>>(){}.getType();
                List<PasswordEntry> passwords = gson.fromJson(sb.toString(), listType);
                if (passwords != null) {
                    int importedCount = 0;
                    int skippedCount = 0;
                    for (PasswordEntry p : passwords) {
                        // 查重：标题、账号、密码一致则跳过
                        PasswordEntry existing = db.passwordDao().findPassword(p.getTitle(), p.getAccount(), p.getPassword());
                        if (existing == null) {
                            p.setId(0); 
                            db.passwordDao().insert(p);
                            importedCount++;
                        } else {
                            skippedCount++;
                        }
                    }
                    showToast("导入完成: 新增 " + importedCount + " 条，跳过重复 " + skippedCount + " 条");
                }
            } catch (Exception e) {
                Log.e(TAG, "导入密码失败", e);
                showToast("导入失败: 格式错误");
            }
        }).start();
    }

    private void exportApiSettings(Uri uri) {
        new Thread(() -> {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                JSONObject json = new JSONObject();
                json.put("ai_key", PrefUtils.getAiApiKey(this));
                json.put("ai_url", PrefUtils.getAiBaseUrl(this));
                json.put("asr_id", PrefUtils.getAsrAppId(this));
                json.put("asr_key", PrefUtils.getAsrApiKey(this));
                json.put("asr_secret", PrefUtils.getAsrSecretKey(this));
                json.put("weather_key", PrefUtils.getWeatherApiKey(this));
                outputStream.write(json.toString(4).getBytes());
                showToast("API 配置导出成功");
            } catch (Exception e) {
                Log.e(TAG, "导出API失败", e);
                showToast("导出失败");
            }
        }).start();
    }

    private void importApiSettings(Uri uri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                PrefUtils.saveAiSettings(this, json.optString("ai_key"), json.optString("ai_url"));
                PrefUtils.saveAsrSettings(this, json.optString("asr_id"), json.optString("asr_key"), json.optString("asr_secret"));
                PrefUtils.saveWeatherApiKey(this, json.optString("weather_key"));
                showToast("API 配置导入成功");
            } catch (Exception e) {
                Log.e(TAG, "导入API失败", e);
                showToast("导入失败: 格式错误");
            }
        }).start();
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        );
    }
}
