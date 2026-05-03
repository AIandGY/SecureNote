package com.aigy.securenote;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.PasswordEntry;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.SecurityManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 密码本适配器
 * 规范：支持全量加解密，并保留长按拖动排序功能（无视觉拖动条）。
 */
public class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.ViewHolder> {

    private static final String TAG = "PasswordAdapter";
    private List<PasswordEntry> passwords = new ArrayList<>();
    private int expandedPosition = -1;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private Toast mToast;

    /**
     * 更新数据源并刷新列表
     */
    public void updateData(List<PasswordEntry> newList) {
        this.passwords = newList;
        notifyDataSetChanged();
    }

    /**
     * 处理条目位置移动（供 ItemTouchHelper 调用）
     */
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(passwords, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PasswordEntry entry = passwords.get(position);
        Context context = holder.itemView.getContext();

        // 默认先读取数据库中的原始值
        String displayTitle = entry.getTitle();
        String displayAccount = entry.getAccount();
        String displayPassword = entry.getPassword();
        String displayNote = entry.getNote();

        // --- 全量解密逻辑 ---
        if (PrefUtils.isEncryptionEnabled(context)) {
            try {
                String wrappedDek = PrefUtils.getWrappedDek(context);
                if (!wrappedDek.isEmpty()) {
                    String dek = SecurityManager.unwrapDEK(wrappedDek);
                    displayTitle = SecurityManager.decryptData(displayTitle, dek);
                    displayAccount = SecurityManager.decryptData(displayAccount, dek);
                    displayPassword = SecurityManager.decryptData(displayPassword, dek);
                    if (displayNote != null && !displayNote.isEmpty()) {
                        displayNote = SecurityManager.decryptData(displayNote, dek);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "实时解密失败: " + entry.getTitle(), e);
            }
        }

        // 绑定解密后的数据
        holder.textTitle.setText(displayTitle);
        setupSecureRow(holder.rowAccount, "账号", displayAccount);
        setupSecureRow(holder.rowPassword, "密码", displayPassword);

        // 动态绑定额外信息
        holder.containerExtraRows.removeAllViews();
        if (displayNote != null && !displayNote.isEmpty()) {
            try {
                Map<String, String> extraData = new Gson().fromJson(displayNote, new TypeToken<Map<String, String>>(){}.getType());
                if (extraData != null && !extraData.isEmpty()) {
                    for (Map.Entry<String, String> extraItem : extraData.entrySet()) {
                        View extraRowView = LayoutInflater.from(context)
                                .inflate(R.layout.item_password_detail_row, holder.containerExtraRows, false);
                        setupSecureRow(extraRowView, extraItem.getKey(), extraItem.getValue());
                        holder.containerExtraRows.addView(extraRowView);
                    }
                }
            } catch (Exception ignored) {}
        }

        /// 展开与收起逻辑
        final boolean isExpanded = position == expandedPosition;
        holder.layoutDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.imgArrow.setRotation(isExpanded ? 180 : 0);

        // --- 修复崩溃点：增加非空判断 ---
        if (holder.layoutHeader != null) {
            holder.layoutHeader.setOnClickListener(v -> {
                int prevExpanded = expandedPosition;
                expandedPosition = isExpanded ? -1 : holder.getAdapterPosition();
                notifyItemChanged(prevExpanded);
                notifyItemChanged(expandedPosition);
            });
        }

        // 编辑按钮
        holder.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), AddPasswordActivity.class);
            intent.putExtra("password_id", entry.getId());
            v.getContext().startActivity(intent);
        });

        // 删除按钮
        final String finalDecryptedTitle = displayTitle;
        holder.btnDelete.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(v.getContext())
                    .setTitle("永久删除")
                    .setMessage("确定要删除“" + finalDecryptedTitle + "”吗？")
                    .setPositiveButton("删除", (dialog, which) -> performDelete(v.getContext(), entry))
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void performDelete(Context context, PasswordEntry entry) {
        new Thread(() -> {
            AppDatabase.getDatabase(context).passwordDao().delete(entry);
        }).start();
    }

    private void setupSecureRow(View rowView, String label, String realValue) {
        TextView tvLabel = rowView.findViewById(R.id.text_label);
        TextView tvValue = rowView.findViewById(R.id.text_value);
        View btnCopy = rowView.findViewById(R.id.btn_copy);

        final String mask = "● ● ● ● ● ●";
        tvLabel.setText(label);
        tvValue.setText(mask);

        View.OnTouchListener secureTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                tvValue.setText(realValue);
                hideHandler.removeCallbacksAndMessages(tvValue);
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("SecureNote", realValue));
                    showSnappyToast(v.getContext(), label + "已复制");
                }
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                hideHandler.postDelayed(() -> {
                    if (tvValue != null) tvValue.setText(mask);
                }, tvValue, 3000);
            }
            return true;
        };

        tvValue.setOnTouchListener(secureTouchListener);
        btnCopy.setOnTouchListener(secureTouchListener);
    }

    private void showSnappyToast(Context context, String message) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        mToast.show();
    }

    @Override
    public int getItemCount() {
        return passwords.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;
        ConstraintLayout layoutDetails;
        LinearLayout containerExtraRows;
        View layoutHeader;
        ImageView imgArrow, btnEdit;
        View rowAccount, rowPassword, btnDelete;

        ViewHolder(View view) {
            super(view);
            textTitle = view.findViewById(R.id.text_title);
            imgArrow = view.findViewById(R.id.img_expand_arrow);
            layoutDetails = view.findViewById(R.id.layout_details);
            containerExtraRows = view.findViewById(R.id.container_extra_rows);
            layoutHeader = view.findViewById(R.id.layout_header);
            btnEdit = view.findViewById(R.id.btn_edit);
            btnDelete = view.findViewById(R.id.btn_delete);
            rowAccount = view.findViewById(R.id.row_account);
            rowPassword = view.findViewById(R.id.row_password);
        }
    }
}
