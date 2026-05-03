package com.aigy.securenote.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 安全管理工具类
 * 采用 DEK (Data Encryption Key) + SHA-256 哈希校验方案
 */
public class SecurityManager {
    private static final String KEY_ALIAS = "MasterVaultKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * 生成硬件级主密钥
     */
    public static void generateMasterKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(60)
                    .build());
            keyGenerator.generateKey();
        }
    }

    /**
     * 生成随机的数据加密密钥 (DEK)
     */
    public static String generateNewDEK() {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);
        return Base64.encodeToString(dek, Base64.NO_WRAP);
    }

    /**
     * 计算密钥的 SHA-256 哈希值（用于校验）
     */
    public static String getSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 使用 DEK 加解密数据
     */
    public static String encryptData(String data, String dekStr) throws Exception {
        if (data == null || data.isEmpty()) return "";
        SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(dekStr, Base64.NO_WRAP), "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data.getBytes());
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public static String decryptData(String encryptedData, String dekStr) throws Exception {
        if (encryptedData == null || encryptedData.isEmpty()) return encryptedData;
        SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(dekStr, Base64.NO_WRAP), "AES");
        byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
        if (combined.length < GCM_IV_LENGTH) return encryptedData;
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(encrypted));
    }

    /**
     * 硬件密钥包装/拆封 DEK
     */
    public static String wrapDEK(String dekStr) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        SecretKey masterKey = (SecretKey) ks.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey);
        byte[] iv = cipher.getIV();
        byte[] wrapped = cipher.doFinal(dekStr.getBytes());
        byte[] combined = new byte[iv.length + wrapped.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(wrapped, 0, combined, iv.length, wrapped.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public static String unwrapDEK(String wrappedDekStr) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        SecretKey masterKey = (SecretKey) ks.getKey(KEY_ALIAS, null);
        byte[] combined = Base64.decode(wrappedDekStr, Base64.NO_WRAP);
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(encrypted));
    }

    public static boolean isMasterKeyExists() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            return ks.containsAlias(KEY_ALIAS);
        } catch (Exception e) { return false; }
    }
}
