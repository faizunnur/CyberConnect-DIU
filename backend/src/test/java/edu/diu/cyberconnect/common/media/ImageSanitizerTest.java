package edu.diu.cyberconnect.common.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {

    @Test
    void squarePhotoIsCentreCroppedScaledAndJpeg() throws IOException {
        byte[] png = png(300, 200, Color.BLUE);

        byte[] jpeg = ImageSanitizer.toSquareJpeg(png, 128);

        assertThat(ImageSanitizer.detectImageType(jpeg)).isEqualTo("image/jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(decoded.getWidth()).isEqualTo(128);
        assertThat(decoded.getHeight()).isEqualTo(128);
        Color centre = new Color(decoded.getRGB(64, 64));
        assertThat(centre.getBlue()).isGreaterThan(200);
        assertThat(centre.getRed()).isLessThan(60);
    }

    @Test
    void reencodeKeepsFormatAndDropsTrailingPayload() throws IOException {
        byte[] png = png(20, 20, Color.RED);
        byte[] withTrailer = new byte[png.length + 16];
        System.arraycopy(png, 0, withTrailer, 0, png.length);
        System.arraycopy("<script>alert</s".getBytes(StandardCharsets.US_ASCII), 0, withTrailer, png.length, 16);

        byte[] clean = ImageSanitizer.reencode(withTrailer, "image/png");

        assertThat(ImageSanitizer.detectImageType(clean)).isEqualTo("image/png");
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
        assertThat(ImageIO.read(new ByteArrayInputStream(clean))).isNotNull();
    }

    @Test
    void rejectsGarbageAndNonImages() {
        assertThat(ImageSanitizer.detectImageType("GIF89a".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThatThrownBy(() -> ImageSanitizer.toSquareJpeg(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2}, 64))
                .isInstanceOf(BadRequestException.class);
    }

    private static byte[] png(int width, int height, Color colour) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
