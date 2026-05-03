package com.aigy.securenote;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.databinding.FragmentPasswordBinding;
import com.aigy.securenote.utils.PrefUtils;

import java.util.concurrent.Executor;

/**
 * 密码本 Fragment
 * 修复编译错误：采用 ItemTouchHelper 默认的长按拖动机制。
 */
public class PasswordsFragment extends Fragment {
    private static final String TAG = "PasswordsFragment";
    private FragmentPasswordBinding binding;
    private PasswordAdapter adapter;
    private AppDatabase db;
    private boolean isUnlocked = false;
    private ItemTouchHelper itemTouchHelper;

    public interface PasswordInteractionListener {
        void onAuthenticationCancelled();
    }

    private PasswordInteractionListener interactionListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PasswordInteractionListener) {
            interactionListener = (PasswordInteractionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getDatabase(requireContext());

        initUI();
        view.post(this::checkSecurityStatus);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // 如果 Fragment 重新显示且尚未解锁，则触发验证
        if (!hidden && !isUnlocked) {
            checkSecurityStatus();
        }
    }

    /**
     * 手动锁定密码本
     */
    public void lockVault() {
        isUnlocked = false;
        if (binding != null) {
            binding.passwordRecyclerView.setVisibility(View.GONE);
            binding.addPasswordFab.setVisibility(View.GONE);
            binding.textEmptyHint.setVisibility(View.GONE);
        }
    }

    private void initUI() {
        binding.passwordRecyclerView.setVisibility(View.GONE);
        binding.addPasswordFab.setVisibility(View.GONE);
        binding.textEmptyHint.setVisibility(View.GONE);

        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultPrimary = typedValue.data;
        int iconColor = PrefUtils.getIconMainColor(requireContext(), defaultPrimary);
        binding.addPasswordFab.setBackgroundTintList(ColorStateList.valueOf(iconColor));

        adapter = new PasswordAdapter();
        binding.passwordRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.passwordRecyclerView.setAdapter(adapter);

        // --- 核心：配置长按拖拽排序 ---
        setupDragAndDrop();

        binding.addPasswordFab.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AddPasswordActivity.class));
        });
    }

    private void setupDragAndDrop() {
        // 创建 ItemTouchHelper.SimpleCallback
        // 允许上下拖动 (UP | DOWN)，不启用滑动删除 (0)
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                adapter.onItemMove(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不实现
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                // 拖动时增加半透明效果，提升交互感
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.7f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 停止拖动后恢复不透明度
                viewHolder.itemView.setAlpha(1.0f);
            }
        };

        itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(binding.passwordRecyclerView);
    }

    private void checkSecurityStatus() {
        if (!PrefUtils.isEncryptionEnabled(requireContext())) {
            unlockVault();
            return;
        }
        authenticateUser();
    }

    private void authenticateUser() {
        if (!isAdded()) return;

        BiometricManager biometricManager = BiometricManager.from(requireContext());
        int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(requireContext(), "加密已开启，请先设置系统指纹或锁屏密码", Toast.LENGTH_LONG).show();
            // 如果无法验证且开启了加密，出于安全考虑也建议返回主页
            if (interactionListener != null) interactionListener.onAuthenticationCancelled();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(requireContext());
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                unlockVault();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    if (interactionListener != null) {
                        interactionListener.onAuthenticationCancelled();
                    }
                } else {
                    Toast.makeText(requireContext(), "验证失败: " + errString, Toast.LENGTH_SHORT).show();
                    // 验证错误（非取消）也可以选择返回主页，避免白屏
                    if (interactionListener != null) interactionListener.onAuthenticationCancelled();
                }
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("密码本解锁")
                .setSubtitle("请验证身份以解密敏感数据")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void unlockVault() {
        isUnlocked = true;
        binding.passwordRecyclerView.setVisibility(View.VISIBLE);
        binding.addPasswordFab.setVisibility(View.VISIBLE);
        setupDataObserver();
    }

    private void setupDataObserver() {
        db.passwordDao().getAllPasswords().observe(getViewLifecycleOwner(), passwords -> {
            if (isUnlocked && passwords != null) {
                adapter.updateData(passwords);
                binding.textEmptyHint.setVisibility(passwords.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
