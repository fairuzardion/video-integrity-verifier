package com.noexperience.integrity.api;

import com.noexperience.integrity.common.InvalidInputException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** An uploaded file copied to a temp path that is deleted on close. */
final class TempUpload implements AutoCloseable {

    private static final Pattern EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,8}$");

    private final Path path;
    private final String displayName;

    private TempUpload(Path path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    static TempUpload from(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("The uploaded file is missing or empty");
        }
        String name = sanitizeName(file.getOriginalFilename());
        Matcher matcher = EXTENSION.matcher(name);
        String extension = matcher.find() ? matcher.group() : ".bin";
        try {
            Path temp = Files.createTempFile("viv-upload-", extension);
            try {
                file.transferTo(temp);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
            return new TempUpload(temp, name);
        } catch (IOException e) {
            throw new InvalidInputException("Cannot store the uploaded file: " + e.getMessage(), e);
        }
    }

    static String sanitizeName(String original) {
        String cleaned = StringUtils.cleanPath(original == null ? "" : original);
        String base = StringUtils.getFilename(cleaned);
        if (base == null || base.isBlank()) {
            base = "upload";
        }
        base = base.replaceAll("\\p{Cntrl}", "_");
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    Path path() {
        return path;
    }

    String displayName() {
        return displayName;
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // temp file will be reclaimed by the OS temp cleaner
        }
    }
}
