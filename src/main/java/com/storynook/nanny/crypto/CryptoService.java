package com.storynook.nanny.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

public class CryptoService {

    public static final String ENC_PREFIX = "enc:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN_BITS = 128;
    private static final int AES_KEY_LEN = 256;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private final Logger logger;

    private CryptoService(SecretKey key, Logger logger) {
        this.key = key;
        this.logger = logger;
    }

    public static CryptoService initialize(File keyFile, Logger logger) throws IOException {
        SecretKey key;
        if (keyFile.exists()) {
            byte[] raw = Files.readAllBytes(keyFile.toPath());
            if (raw.length != AES_KEY_LEN / 8) {
                throw new IOException("Invalid key length in " + keyFile + " (expected 32 bytes, got " + raw.length + ")");
            }
            key = new SecretKeySpec(raw, "AES");
        } else {
            try {
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(AES_KEY_LEN);
                key = kg.generateKey();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException("Failed to generate AES key", e);
            }
            File parent = keyFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.write(keyFile.toPath(), key.getEncoded(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            boolean permOk = keyFile.setReadable(false, false)
                    & keyFile.setReadable(true, true)
                    & keyFile.setWritable(false, false)
                    & keyFile.setWritable(true, true);
            if (!permOk) {
                logger.warning("[Crypto] Could not restrict key file permissions on this OS. " +
                        "Manually restrict access to: " + keyFile.getAbsolutePath());
            }
            logger.info("[Crypto] Generated new AES-256 key at: " + keyFile.getAbsolutePath()
                    + " — keep this file secret and include it in any server migration (see docs/security/hardening.md).");
        }
        return new CryptoService(key, logger);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        // Idempotency guard — assumes legitimate plaintext never starts with "enc:"
        // (player names, tier names, and API keys will not start with this prefix).
        if (plaintext.startsWith(ENC_PREFIX)) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || !value.startsWith(ENC_PREFIX)) return value;
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(ENC_PREFIX.length()));
            if (all.length < GCM_IV_LEN + 16) throw new IllegalArgumentException("ciphertext too short");
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(all, 0, iv, 0, GCM_IV_LEN);
            byte[] ct = new byte[all.length - GCM_IV_LEN];
            System.arraycopy(all, GCM_IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed (key file may have been replaced)", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}
