package edu.diu.cyberconnect.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the API exactly like the browser does: cookies carried between calls, CSRF cookie echoed in the
 * X-XSRF-TOKEN header on mutations. One {@link Session} per simulated browser.
 */
public class ApiClient {

    public static final String SESSION_COOKIE = "CCSESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    /** Mutable cookie jar for one simulated browser. */
    public static class Session {
        final Map<String, Cookie> cookies = new HashMap<>();

        public boolean hasSession() {
            return cookies.containsKey(SESSION_COOKIE);
        }

        public Cookie sessionCookie() {
            return cookies.get(SESSION_COOKIE);
        }
    }

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ApiClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public Session newSession() throws Exception {
        Session session = new Session();
        get("/api/v1/auth/csrf", session);
        return session;
    }

    public MvcResult get(String path, Session session) throws Exception {
        return exchange(MockMvcRequestBuilders.get(path), session);
    }

    public MvcResult post(String path, Object body, Session session) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return exchange(request, session);
    }

    public MvcResult put(String path, Object body, Session session) throws Exception {
        return exchange(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), session);
    }

    public MvcResult delete(String path, Session session) throws Exception {
        return exchange(MockMvcRequestBuilders.delete(path), session);
    }

    public MvcResult upload(String path, byte[] bytes, String filename, String contentType, String documentType,
            Session session) throws Exception {
        return exchange(MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .param("documentType", documentType), session);
    }

    public MvcResult exchange(AbstractMockHttpServletRequestBuilder<?> request, Session session) throws Exception {
        if (!session.cookies.isEmpty()) {
            request.cookie(session.cookies.values().toArray(new Cookie[0]));
        }
        Cookie csrf = session.cookies.get(CSRF_COOKIE);
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf.getValue());
        }
        request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON, MediaType.ALL);
        MvcResult result = mvc.perform(request).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                session.cookies.remove(cookie.getName());
            } else {
                session.cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }

    public JsonNode body(MvcResult result) {
        String content = content(result);
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    public String code(MvcResult result) {
        JsonNode node = body(result).get("code");
        return node == null ? null : node.asString();
    }

    public void assertStatus(MvcResult result, int expected) {
        assertThat(result.getResponse().getStatus())
                .as("status for %s %s: %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                        content(result))
                .isEqualTo(expected);
    }

    public static String content(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    /** A clearly fictional "ID card" image, generated on the fly; never a real document. */
    public static byte[] fictionalIdCardPng() {
        BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 640, 400);
        g.setColor(Color.DARK_GRAY);
        g.drawString("FICTIONAL TEST ID CARD - NOT A REAL DOCUMENT", 40, 60);
        g.drawString("Example Student  ID 000-00-0000", 40, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
