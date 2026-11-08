package edu.diu.cyberconnect.community;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Photos and documents on posts: intake checks, claiming rules, visibility-checked serving, cleanup on delete. */
class PostAttachmentIT extends AbstractIntegrationTest {

    private static final String UPLOAD = "/api/v1/posts/attachments";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void photosAreSanitisedClaimedAndServedOnlyToViewersOfThePost() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "att-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "att-bob");
        TestActors.Actor carol = actors.verifiedMember(api, "att-carol");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        ApiClient.Session c = actors.login(api, carol);

        // A large PNG with a text chunk comes back as a JPEG scaled to 2048 px, without the metadata.
        MvcResult up = api.upload(UPLOAD, pngWithText(3000, 1500, "GPS secret location"), "holiday.png", "image/png", "", a);
        api.assertStatus(up, 201);
        JsonNode photo = api.body(up);
        assertThat(photo.get("kind").asString()).isEqualTo("IMAGE");
        assertThat(photo.get("contentType").asString()).isEqualTo("image/jpeg");
        assertThat(photo.get("width").asInt()).isEqualTo(2048);
        assertThat(photo.get("height").asInt()).isEqualTo(1024);
        assertThat(photo.get("filename").asString()).isEqualTo("holiday.jpg");
        assertThat(photo.get("url").isNull()).isTrue();
        String photoId = photo.get("id").asString();

        // Someone else cannot claim Alice's upload.
        MvcResult stolen = api.post("/api/v1/posts", post("Not mine", List.of(photoId)), b);
        api.assertStatus(stolen, 400);
        assertThat(api.code(stolen)).isEqualTo("ATTACHMENT_INVALID");

        // A post may consist of photos only.
        MvcResult created = api.post("/api/v1/posts", post("", List.of(photoId)), a);
        api.assertStatus(created, 201);
        JsonNode view = api.body(created);
        String postId = view.get("id").asString();
        JsonNode attachments = view.get("attachments");
        assertThat(attachments.size()).isEqualTo(1);
        String url = attachments.get(0).get("url").asString();
        assertThat(url).isEqualTo("/api/v1/posts/" + postId + "/attachments/" + photoId);

        // Already claimed: cannot be reused for a second post.
        api.assertStatus(api.post("/api/v1/posts", post("again", List.of(photoId)), a), 400);

        MvcResult image = api.get(url, b);
        api.assertStatus(image, 200);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(image.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        byte[] served = image.getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(served)).getWidth()).isEqualTo(2048);
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("GPS secret location");

        // Blocked viewers and anonymous callers get nothing.
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", carol.id().toString()), a), 204);
        api.assertStatus(api.get(url, c), 404);
        api.assertStatus(api.get(url, api.newSession()), 401);

        // Deleting the post removes the attachment.
        api.assertStatus(api.delete("/api/v1/posts/" + postId, a), 204);
        api.assertStatus(api.get(url, a), 404);
    }

    @Test
    void documentsAreValidatedByContentAndDownloadedAsAttachments() throws Exception {
        TestActors.Actor dana = actors.verifiedMember(api, "att-dana");
        TestActors.Actor erin = actors.verifiedMember(api, "att-erin");
        ApiClient.Session d = actors.login(api, dana);
        ApiClient.Session e = actors.login(api, erin);

        byte[] pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n"
                .getBytes(StandardCharsets.US_ASCII);
        JsonNode doc = api.body(api.upload(UPLOAD, pdf, "../../CV final.pdf", "application/pdf", "", d));
        assertThat(doc.get("kind").asString()).isEqualTo("FILE");
        assertThat(doc.get("contentType").asString()).isEqualTo("application/pdf");
        assertThat(doc.get("filename").asString()).isEqualTo("CV final.pdf");

        // Declared type is ignored: an executable named .pdf is refused, so is a macro-enabled Word file.
        MvcResult exe = api.upload(UPLOAD, "MZ\u0090\u0000fake executable".getBytes(StandardCharsets.ISO_8859_1),
                "invoice.pdf", "application/pdf", "", d);
        api.assertStatus(exe, 400);
        assertThat(api.code(exe)).isEqualTo("UNSUPPORTED_FILE_TYPE");
        api.assertStatus(api.upload(UPLOAD, ooxml("word/document.xml", "word/vbaProject.bin"), "report.docx",
                "application/octet-stream", "", d), 400);
        JsonNode docx = api.body(api.upload(UPLOAD, ooxml("word/document.xml"), "report.docx", "application/octet-stream", "", d));
        assertThat(docx.get("contentType").asString()).contains("wordprocessingml");

        // A document cannot be mixed with photos, and only one document per post.
        String photoId = api.body(api.upload(UPLOAD, pngWithText(400, 300, "x"), "a.png", "image/png", "", d)).get("id").asString();
        MvcResult mixed = api.post("/api/v1/posts", post("mix", List.of(doc.get("id").asString(), photoId)), d);
        api.assertStatus(mixed, 400);
        assertThat(api.code(mixed)).isEqualTo("ATTACHMENT_MIX");

        // The composer can discard an upload; the PDF is posted.
        api.assertStatus(api.delete(UPLOAD + "/" + photoId, d), 204);
        api.assertStatus(api.delete(UPLOAD + "/" + docx.get("id").asString(), e), 404);
        JsonNode created = api.body(api.post("/api/v1/posts", post("My CV", List.of(doc.get("id").asString())), d));
        String url = created.get("attachments").get(0).get("url").asString();

        MvcResult download = api.get(url, e);
        api.assertStatus(download, 200);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment").contains("CV final.pdf");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(pdf);

        // Feed shows the attachment to other members.
        JsonNode feed = api.body(api.get("/api/v1/feed?q=CV", e));
        assertThat(feed.get("content").get(0).get("attachments").get(0).get("kind").asString()).isEqualTo("FILE");
    }

    private static Map<String, Object> post(String body, List<String> attachmentIds) {
        Map<String, Object> request = new HashMap<>();
        request.put("intent", "TECHNICAL_DISCUSSION");
        request.put("body", body);
        request.put("attachmentIds", attachmentIds);
        return request;
    }

    private static byte[] pngWithText(int width, int height, String text) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] png = out.toByteArray();
        // Append a tEXt chunk before IEND (metadata the sanitiser must drop).
        byte[] keyword = ("Comment\u0000" + text).getBytes(StandardCharsets.ISO_8859_1);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("tEXt".getBytes(StandardCharsets.US_ASCII));
        crc.update(keyword);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.write(java.nio.ByteBuffer.allocate(4).putInt(keyword.length).array());
        chunk.write("tEXt".getBytes(StandardCharsets.US_ASCII));
        chunk.write(keyword);
        chunk.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        int iend = png.length - 12;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(png, 0, iend);
        result.write(chunk.toByteArray());
        result.write(png, iend, 12);
        return result.toByteArray();
    }

    private static byte[] ooxml(String... parts) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String part : parts) {
                zip.putNextEntry(new ZipEntry(part));
                zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
