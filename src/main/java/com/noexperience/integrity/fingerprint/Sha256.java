package com.noexperience.integrity.fingerprint;

import com.noexperience.integrity.common.InvalidInputException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers. */
public final class Sha256 {

    private Sha256() {
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashHex(byte[] data) {
        return hex(newDigest().digest(data));
    }

    public static String hashHex(String utf8) {
        return hashHex(utf8.getBytes(StandardCharsets.UTF_8));
    }

    /** Streams the whole file through SHA-256. */
    public static String hashFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new InvalidInputException("File does not exist: " + file);
        }
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new InvalidInputException("Cannot read file " + file + ": " + e.getMessage(), e);
        }
        return hex(digest.digest());
    }
}
