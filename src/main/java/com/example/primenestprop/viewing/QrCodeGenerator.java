package com.example.primenestprop.viewing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Generates a real, scannable QR code (PNG, base64-encoded as a data URI) - not a placeholder icon. */
final class QrCodeGenerator {
    private QrCodeGenerator() {
    }

    static String toDataUri(String content, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate QR code", ex);
        }
    }
}
