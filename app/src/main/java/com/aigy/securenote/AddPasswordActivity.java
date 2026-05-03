package com.aigy.securenote;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.PasswordEntry;
import com.aigy.securenote.databinding.ActivityAddPasswordBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.SecurityManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 密码添加/编辑页面
 * 优化：支持加密状态标识符，防止重复加密
 */
public class AddPasswordActivity extends AppCompatActivity {

    private static final String TAG = "AddPasswordActivity";
    private ActivityAddPasswordBinding binding;
    private AppDatabase db;
    private final List<View> extraRows = new ArrayList<>();
    private long editingId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getDatabase(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        editingId = getIntent().getLongExtra("password_id", -1);
        if (editingId != -1) {
            loadExistingData();
            binding.toolbar.setTitle("编辑密码");
        } else {
            binding.toolbar.setTitle("添加密码");
        }

        binding.btnSave.setOnClickListener(v -> savePassword());
        binding.btnAddExtra.setOnClickListener(v -> addNewExtraRow(null, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCustomThemeColor();
    }

    private void applyCustomThemeColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;
        int textColor = PrefUtils.getTextMainColor(this, defaultColor);
        binding.btnSave.setTextColor(textColor);
    }

    private void loadExistingData() {
        new Thread(() -> {
            PasswordEntry entry = db.passwordDao().getPasswordById(editingId);
            if (entry != null) {
                try {
                    String decryptedTitle = entry.getTitle();
                    String decryptedAccount = entry.getAccount();
                    String decryptedPassword = entry.getPassword();
                    String decryptedNote = entry.getNote();

                    // 只有当条目确实被标记为加密时，才进行解密
                    if (entry.getIsEncrypted() == 1 && PrefUtils.isEncryptionEnabled(this)) {
                        String wrappedDek = PrefUtils.getWrappedDek(this);
                        if (!wrappedDek.isEmpty()) {
                            try {
                                String dek = SecurityManager.unwrapDEK(wrappedDek);
                                decryptedTitle = SecurityManager.decryptData(entry.getTitle(), dek);
                                decryptedAccount = SecurityManager.decryptData(entry.getAccount(), dek);
                                decryptedPassword = SecurityManager.decryptData(entry.getPassword(), dek);
                                if (entry.getNote() != null && !entry.getNote().isEmpty()) {
                                    decryptedNote = SecurityManager.decryptData(entry.getNote(), dek);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "解密失败", e);
                            }
                        }
                    }

                    final String finalTitle = decryptedTitle;
                    final String finalAcc = decryptedAccount;
                    final String finalPwd = decryptedPassword;
                    final String finalNote = decryptedNote;

                    runOnUiThread(() -> {
                        binding.editTitle.setText(finalTitle);
                        binding.editAccount.setText(finalAcc);
                        binding.editPassword.setText(finalPwd);

                        if (finalNote != null && !finalNote.isEmpty()) {
                            try {
                                Map<String, String> extraData = new Gson().fromJson(finalNote,
                                        new TypeToken<Map<String, String>>(){}.getType());
                                if (extraData != null) {
                                    for (Map.Entry<String, String> item : extraData.entrySet()) {
                                        addNewExtraRow(item.getKey(), item.getValue());
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "加载数据出错", e);
                }
            }
        }).start();
    }

    private void addNewExtraRow(String label, String value) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.item_extra_info, binding.containerExtraInfo, false);
        android.widget.EditText editLabel = rowView.findViewById(R.id.edit_extra_label);
        android.widget.EditText editValue = rowView.findViewById(R.id.edit_extra_value);
        if (label != null) editLabel.setText(label);
        if (value != null) editValue.setText(value);
        rowView.findViewById(R.id.btn_delete_row).setOnClickListener(v -> {
            binding.containerExtraInfo.removeView(rowView);
            extraRows.remove(rowView);
        });
        binding.containerExtraInfo.addView(rowView);
        extraRows.add(rowView);
    }

    private void savePassword() {
        String title = binding.editTitle.getText().toString().trim();
        String account = binding.editAccount.getText().toString().trim();
        String password = binding.editPassword.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> extraData = new HashMap<>();
        for (View row : extraRows) {
            android.widget.EditText editLabel = row.findViewById(R.id.edit_extra_label);
            android.widget.EditText editValue = row.findViewById(R.id.edit_extra_value);
            String l = editLabel.getText().toString().trim();
            String v = editValue.getText().toString().trim();
            if (!l.isEmpty() || !v.isEmpty()) extraData.put(l, v);
        }
        String noteJson = new Gson().toJson(extraData);

        new Thread(() -> {
            try {
                PasswordEntry entry = (editingId != -1) ? db.passwordDao().getPasswordById(editingId) : new PasswordEntry();
                if (entry == null) return;

                entry.setTimestamp(System.currentTimeMillis());

                if (PrefUtils.isEncryptionEnabled(this)) {
                    String wrappedDek = PrefUtils.getWrappedDek(this);
                    if (!wrappedDek.isEmpty()) {
                        String dek = SecurityManager.unwrapDEK(wrappedDek);
                        entry.setTitle(SecurityManager.encryptData(title, dek));
                        entry.setAccount(SecurityManager.encryptData(account, dek));
                        entry.setPassword(SecurityManager.encryptData(password, dek));
                        entry.setNote(SecurityManager.encryptData(noteJson, dek));
                        entry.setIsEncrypted(1); // 标记为已加密
                    } else {
                        throw new Exception("加密已开启但未找到有效密钥");
                    }
                } else {
                    entry.setTitle(title);
                    entry.setAccount(account); // 修复：这里之前误写成了 entry.Account(account)
                    entry.setPassword(password);
                    entry.setNote(noteJson);
                    entry.setIsEncrypted(0); // 标记为未加密
                }

                if (editingId != -1) db.passwordDao().update(entry);
                else db.passwordDao().insert(entry);

                runOnUiThread(() -> {
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "保存失败", e);
                runOnUiThread(() -> Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}