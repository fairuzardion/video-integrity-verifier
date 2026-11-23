package com.noexperience.integrity.common;

/** FFmpeg/FFprobe or image decoding failed, or the media file is unusable. */
public class MediaProcessingException extends IntegrityException {
    public MediaProcessingException(String message) {
        super(message);
    }

    public MediaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
