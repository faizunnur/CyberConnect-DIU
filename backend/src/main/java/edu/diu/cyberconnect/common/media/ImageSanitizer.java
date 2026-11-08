package edu.diu.cyberconnect.common.media;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Decodes untrusted JPEG/PNG bytes and writes a fresh image, which discards every metadata block (EXIF, GPS, ICC,
 * XMP) and any trailing payload. Dimension guards stop decompression bombs before pixels are allocated.
 */
public final class ImageSanitizer {

    public static final int MAX_DIMENSION = 8000;
    public static final long MAX_PIXELS = 40_000_000L;

    private ImageSanitizer() {
    }

    /** Magic-byte detection for the two supported raster formats; {@code null} for anything else. */
    public static String detectImageType(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    /** Re-encodes in the same format (PNG stays PNG, JPEG stays JPEG) with all metadata dropped. */
    public static byte[] reencode(byte[] original, String contentType) {
        String format = "image/png".equals(contentType) ? "png" : "jpeg";
        BufferedImage image = decode(original);
        if ("jpeg".equals(format)) {
            image = toRgb(image);
        }
        return write(image, format);
    }

    /** Centre-crops to a square, scales to {@code size} pixels and writes a JPEG (used for profile photos). */
    public static byte[] toSquareJpeg(byte[] original, int size) {
        BufferedImage source = decode(original);
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return write(target, "jpeg");
    }

    /** A re-encoded JPEG with its final pixel size. */
    public record Fitted(byte[] bytes, int width, int height) {
    }

    /**
     * Re-encodes as JPEG (metadata dropped, transparency flattened on white), scaled down so the longer edge is at most
     * {@code maxEdge} pixels; smaller images keep their size. Used for photos attached to posts.
     */
    public static Fitted fitJpeg(byte[] original, int maxEdge) {
        BufferedImage source = decode(original);
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage target = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return new Fitted(write(target, "jpeg"), tw, th);
    }

    private static BufferedImage decode(byte[] original) {
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
                if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("IMAGE_TOO_LARGE", "The image dimensions are too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be decoded.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be decoded.");
        }
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IllegalStateException("No image writer for " + format);
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
        } catch (IOException e) {
            throw new BadRequestException("IMAGE_UNREADABLE", "The image could not be encoded.");
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
