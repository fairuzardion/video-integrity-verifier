package com.noexperience.integrity.fingerprint;

import com.noexperience.integrity.common.MediaProcessingException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Computes the cryptographic and perceptual fingerprint of a segment from its extracted frame images. */
@Component
public class SegmentFingerprinter {

    public SegmentFingerprint fingerprint(List<Path> framePaths) {
        MessageDigest segmentDigest = Sha256.newDigest();
        segmentDigest.update(ByteBuffer.allocate(4).putInt(framePaths.size()).array());
        long[] perceptual = new long[framePaths.size()];
        for (int i = 0; i < framePaths.size(); i++) {
            BufferedImage image = read(framePaths.get(i));
            segmentDigest.update(pixelDigest(image));
            perceptual[i] = PerceptualHasher.dHash(image);
        }
        return new SegmentFingerprint(
                Sha256.hex(segmentDigest.digest()),
                SegmentPerceptualHash.encode(perceptual),
                framePaths.size());
    }

    /** SHA-256 over the image dimensions and raw RGB samples, independent of the PNG encoder. */
    static byte[] pixelDigest(BufferedImage image) {
        MessageDigest digest = Sha256.newDigest();
        int w = image.getWidth();
        int h = image.getHeight();
        digest.update(ByteBuffer.allocate(8).putInt(w).putInt(h).array());
        int[] row = new int[w];
        byte[] rgb = new byte[w * 3];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                rgb[3 * x] = (byte) (p >> 16);
                rgb[3 * x + 1] = (byte) (p >> 8);
                rgb[3 * x + 2] = (byte) p;
            }
            digest.update(rgb);
        }
        return digest.digest();
    }

    private static BufferedImage read(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new MediaProcessingException("Frame is not a decodable image: " + path);
            }
            return image;
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot read frame " + path + ": " + e.getMessage(), e);
        }
    }
}
