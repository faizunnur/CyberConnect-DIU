package edu.diu.cyberconnect.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.common.config.CryptoProperties;
import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.crypto.AesGcmEncryptor;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.scanning.MalwareScanner;
import edu.diu.cyberconnect.common.scanning.ScanResult;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.support.ApiClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentPipelineTest {

    private final SystemSettings settings = mock(SystemSettings.class);
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AesGcmEncryptor encryptor = new AesGcmEncryptor(
            new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32]), "test"));
    private final StorageProperties storageProperties = new StorageProperties("http://x", "us-east-1", "a", "b", true,
            false, new StorageProperties.Buckets("docs", "media"));
    private DocumentPipeline pipeline;

    @BeforeEach
    void setUp() {
        when(settings.getInt(SettingKeys.DOCUMENT_MAX_FILE_BYTES)).thenReturn(8 * 1024 * 1024);
        when(scanner.scan(any(), anyLong())).thenReturn(ScanResult.clean());
        when(scanner.engine()).thenReturn("mock");
        pipeline = new DocumentPipeline(settings, scanner, encryptor, storage, storageProperties);
    }

    @Test
    void detectsTypesByMagicBytesNotByName() {
        assertThat(DocumentPipeline.detectType(ApiClient.fictionalIdCardPng())).isEqualTo("image/png");
        assertThat(DocumentPipeline.detectType("%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(DocumentPipeline.detectType("MZ executable".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(DocumentPipeline.detectType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
    }

    @Test
    void reencodesImagesEncryptsAndStoresUnderOpaqueKey() throws Exception {
        UUID submissionId = UUID.randomUUID();
        byte[] png = ApiClient.fictionalIdCardPng();

        DocumentPipeline.Stored stored = pipeline.process(submissionId, png, "image/png");

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.storageKey()).startsWith("documents/" + submissionId + "/" + stored.documentId());
        assertThat(stored.scanEngine()).isEqualTo("mock");
        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
        verify(storage).put(eq("docs"), eq(stored.storageKey()), content.capture(), anyLong(),
                eq("application/octet-stream"), any());
        byte[] ciphertext = content.getValue().readAllBytes();
        assertThat(DocumentPipeline.detectType(ciphertext)).as("stored bytes are encrypted").isNull();
        byte[] plain = pipeline.decrypt(ciphertext, stored.documentId());
        assertThat(DocumentPipeline.detectType(plain)).isEqualTo("image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(plain))).isNotNull();
    }

    @Test
    void rejectsUnsupportedTooLargeAndActivePdfs() {
        assertThatThrownBy(() -> pipeline.process(UUID.randomUUID(), "MZ".getBytes(StandardCharsets.US_ASCII), null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("JPEG");
        when(settings.getInt(SettingKeys.DOCUMENT_MAX_FILE_BYTES)).thenReturn(10);
        assertThatThrownBy(() -> pipeline.process(UUID.randomUUID(), ApiClient.fictionalIdCardPng(), null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("maximum");
        when(settings.getInt(SettingKeys.DOCUMENT_MAX_FILE_BYTES)).thenReturn(8 * 1024 * 1024);
        byte[] pdf = "%PDF-1.7\n1 0 obj << /S /JavaScript /JS (app.alert(1)) >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> pipeline.process(UUID.randomUUID(), pdf, "application/pdf"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("scripts");
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void neverStoresInfectedOrUnscannedContent() {
        when(scanner.scan(any(), anyLong())).thenReturn(ScanResult.infected("Eicar-Test-Signature"));
        assertThatThrownBy(() -> pipeline.process(UUID.randomUUID(), ApiClient.fictionalIdCardPng(), null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("malware");
        when(scanner.scan(any(), anyLong())).thenReturn(ScanResult.error("down"));
        assertThatThrownBy(() -> pipeline.process(UUID.randomUUID(), ApiClient.fictionalIdCardPng(), null))
                .isInstanceOf(DocumentPipeline.ScanUnavailableException.class);
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString(), any());
    }
}
