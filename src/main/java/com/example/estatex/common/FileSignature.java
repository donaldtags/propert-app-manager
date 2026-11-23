package com.example.estatex.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Checks what an uploaded file really is from its first bytes ("magic numbers"), instead of
 * trusting the Content-Type or file name the sender chose. A web page renamed to photo.png, or a
 * script labelled application/pdf, is rejected before it is stored.
 *
 * Also fingerprints files with SHA-256: stored next to documents it proves a file hasn't been
 * swapped since upload, and on photos it spots the same picture reused across listings even
 * when it was re-uploaded under a different name.
 */
public final class FileSignature {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String DOC = "application/msword";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private FileSignature() {}

    /** The detected media type, or null when the bytes match none of the types we accept. */
    public static String detect(byte[] head) {
        if (head == null || head.length < 4) return null;
        if (startsWith(head, 0x25, 0x50, 0x44, 0x46, 0x2D)) return PDF;              // %PDF-
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return JPEG;
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;
        if (head.length >= 12 && startsWith(head, 0x52, 0x49, 0x46, 0x46)           // RIFF....WEBP
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return WEBP;
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) return DOC; // OLE2
        if (startsWith(head, 0x50, 0x4B, 0x03, 0x04)) return DOCX;                   // ZIP (Office Open XML)
        return null;
    }

    /**
     * Reads the file's first bytes and returns its real type, which must be one of {@code allowed};
     * otherwise a 400 with {@code message}. Callers store and serve the file under this type.
     */
    public static String require(MultipartFile file, Set<String> allowed, String message) {
        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = detect(in.readNBytes(16));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        if (detected == null || !allowed.contains(detected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return detected;
    }

    /** File extension (with dot) for a type returned by {@link #detect}. */
    public static String extension(String mediaType) {
        return switch (mediaType) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case WEBP -> ".webp";
            case DOC -> ".doc";
            case DOCX -> ".docx";
            default -> "";
        };
    }

    public static String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
