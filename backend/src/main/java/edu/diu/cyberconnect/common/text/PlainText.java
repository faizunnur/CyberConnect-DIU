package edu.diu.cyberconnect.common.text;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text sanitisation for user-generated content. The platform never renders HTML from members: text is stored as
 * typed (minus control characters, zero-width characters and excessive blank lines) and the frontend renders it as
 * text nodes. Markup therefore stays inert even if it reaches the database.
 */
public final class PlainText {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200F\\u2028-\\u202E\\u2060-\\u2064\\uFEFF]");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+\\n");
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}_#])#([\\p{L}\\p{M}\\p{N}_]{1,64})");

    public static final int MAX_HASHTAGS = 10;

    private PlainText() {
    }

    /** Normalises to NFC, strips control/zero-width characters, trims and collapses blank lines. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL.matcher(text).replaceAll("");
        text = ZERO_WIDTH.matcher(text).replaceAll("");
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = EXTRA_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    /** Lower-cased, de-duplicated hashtags in order of appearance (at most {@link #MAX_HASHTAGS}). */
    public static List<String> hashtags(String text) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(text == null ? "" : text);
        while (matcher.find() && tags.size() < MAX_HASHTAGS) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tags);
    }

    public static String normalizeTag(String tag) {
        String t = tag == null ? "" : tag.trim().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        return t.matches("[\\p{L}\\p{M}\\p{N}_]{1,64}") ? t : "";
    }
}
