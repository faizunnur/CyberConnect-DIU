package edu.diu.cyberconnect.common.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Content-based detection for the document formats members may share: PDF and the Office Open XML formats (Word,
 * PowerPoint, Excel). The declared file name only selects which OOXML flavour is expected; the bytes must match it.
 * Macro-enabled packages (any {@code vbaProject.bin} part) are refused. Zip entries are listed, never decompressed,
 * and the entry count is capped, so archive bombs cannot exhaust memory here.
 */
public final class DocumentTypes {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int MAX_ZIP_ENTRIES = 5_000;

    private DocumentTypes() {
    }

    /** The detected content type, or {@code null} when the bytes are not an accepted document. */
    public static String detect(byte[] bytes, String filename) {
        if (bytes == null || bytes.length < 8) {
            return null;
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return PDF;
        }
        boolean zip = bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
        if (!zip) {
            return null;
        }
        String extension = extension(filename);
        String expectedPart;
        String type;
        switch (extension) {
            case "docx" -> { expectedPart = "word/"; type = DOCX; }
            case "pptx" -> { expectedPart = "ppt/"; type = PPTX; }
            case "xlsx" -> { expectedPart = "xl/"; type = XLSX; }
            default -> { return null; }
        }
        boolean contentTypes = false;
        boolean mainPart = false;
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    return null;
                }
                String name = entry.getName();
                if (name.toLowerCase(Locale.ROOT).contains("vbaproject")) {
                    return null;
                }
                if ("[Content_Types].xml".equals(name)) {
                    contentTypes = true;
                }
                if (name.startsWith(expectedPart)) {
                    mainPart = true;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return contentTypes && mainPart ? type : null;
    }

    /** Extension used when serving a document of the given type. */
    public static String extensionFor(String contentType) {
        return switch (contentType) {
            case PDF -> "pdf";
            case DOCX -> "docx";
            case PPTX -> "pptx";
            case XLSX -> "xlsx";
            default -> "bin";
        };
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
