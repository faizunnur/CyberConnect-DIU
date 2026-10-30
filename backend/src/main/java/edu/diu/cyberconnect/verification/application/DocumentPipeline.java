package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.crypto.AesGcmEncryptor;
import edu.diu.cyberconnect.common.exception.ApiException;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.scanning.MalwareScanner;
import edu.diu.cyberconnect.common.scanning.ScanResult;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Identity-document intake (ADR-0007): size limit → magic-byte allowlist → image re-encode (drops EXIF/GPS) or PDF
 * structural checks → malware scan → AES-256-GCM encryption → private object storage under an opaque key.
 */
@Component
public class DocumentPipeline {

    public record Stored(UUID documentId, String storageKey, String contentType, long sizeBytes, String sha256,
            String scanEngine, String encryptionKeyId) {
    }

    private static final int MAX_IMAGE_DIMENSION = 8000;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final List<String> PDF_FORBIDDEN_TOKENS = List.of("/JavaScript", "/JS", "/Launch", "/EmbeddedFile",
            "/RichMedia", "/XFA");

    private final SystemSettings settings;
    private final MalwareScanner scanner;
    private final AesGcmEncryptor encryptor;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;

    public DocumentPipeline(SystemSettings settings, MalwareScanner scanner, AesGcmEncryptor encryptor,
            ObjectStorage storage, StorageProperties storageProperties) {
        this.settings = settings;
        this.scanner = scanner;
        this.encryptor = encryptor;
        this.storage = storage;
        this.storageProperties = storageProperties;
    }

    public int maxFileBytes() {
        return settings.getInt(SettingKeys.DOCUMENT_MAX_FILE_BYTES);
    }

    public Stored process(UUID submissionId, byte[] original, String declaredContentType) {
        if (original == null || original.length == 0) {
            throw new BadRequestException("FILE_EMPTY", "The uploaded file is empty.");
        }
        if (original.length > maxFileBytes()) {
            throw new BadRequestException("UPLOAD_TOO_LARGE", "The file exceeds the maximum allowed size.");
        }
        String detected = detectType(original);
        if (detected == null) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG and PDF files are accepted.");
        }

        byte[] sanitized = switch (detected) {
            case "image/jpeg", "image/png" -> reencodeImage(original, detected);
            case "application/pdf" -> checkPdf(original);
            default -> throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG and PDF files are accepted.");
        };

        ScanResult scan = scanner.scan(new ByteArrayInputStream(sanitized), sanitized.length);
        if (scan.status() == ScanResult.Status.INFECTED) {
            throw new BadRequestException("DOCUMENT_REJECTED_MALWARE", "The file was rejected by the malware scanner.");
        }
        if (scan.status() == ScanResult.Status.ERROR) {
            throw new ScanUnavailableException();
        }

        UUID documentId = UUID.randomUUID();
        byte[] encrypted = encryptor.encrypt(sanitized, documentId.toString().getBytes(StandardCharsets.UTF_8));
        String key = "documents/" + submissionId + "/" + documentId;
        storage.put(storageProperties.buckets().documents(), key, new ByteArrayInputStream(encrypted),
                encrypted.length, "application/octet-stream",
                Map.of("enc-key-id", encryptor.keyId(), "original-content-type", detected));
        return new Stored(documentId, key, detected, sanitized.length, sha256(sanitized), scanner.engine(),
                encryptor.keyId());
    }

    public byte[] decrypt(byte[] encrypted, UUID documentId) {
        return encryptor.decrypt(encrypted, documentId.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String detectType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F'
                && bytes[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    /** Decodes and re-encodes the image, which discards all metadata (EXIF, GPS, embedded profiles). */
    private static byte[] reencodeImage(byte[] original, String contentType) {
        String format = "image/png".equals(contentType) ? "png" : "jpeg";
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BadRequestException("IMAGE_TOO_LARGE", "The image dimensions are too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be decoded.");
                }
                if ("jpeg".equals(format) && image.getType() != BufferedImage.TYPE_INT_RGB) {
                    BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgb.getGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
                    image = rgb;
                }
                return write(image, format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be decoded.");
        }
    }

    private static byte[] write(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IOException("No writer for " + format);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(format) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.9f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Rejects PDFs carrying active content or attachments; evidence documents never need them. */
    private static byte[] checkPdf(byte[] pdf) {
        String ascii = new String(pdf, StandardCharsets.ISO_8859_1);
        for (String token : PDF_FORBIDDEN_TOKENS) {
            if (ascii.contains(token)) {
                throw new BadRequestException("PDF_ACTIVE_CONTENT", "PDFs with scripts or attachments are not accepted.");
            }
        }
        if (!ascii.contains("%%EOF")) {
            throw new BadRequestException("PDF_MALFORMED", "The PDF appears to be incomplete.");
        }
        return pdf;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fail closed when the scanner is unavailable: the document is never stored. */
    public static class ScanUnavailableException extends ApiException {
        public ScanUnavailableException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "SCAN_UNAVAILABLE",
                    "The malware scanner is unavailable. Please try again later.");
        }
    }
}
