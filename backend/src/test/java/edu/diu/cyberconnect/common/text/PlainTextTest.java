package edu.diu.cyberconnect.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlainTextTest {

    @Test
    void stripsControlAndZeroWidthCharactersAndCollapsesBlankLines() {
        String raw = "  Hello\u0000 world​\r\n\r\n\r\n\r\nSecond line \t\n<b>kept as text</b>\u0007  ";
        assertThat(PlainText.sanitize(raw)).isEqualTo("Hello world\n\nSecond line\n<b>kept as text</b>");
        assertThat(PlainText.sanitize(null)).isEmpty();
        assertThat(PlainText.sanitize("\t\n ")).isEmpty();
    }

    @Test
    void extractsLowercasedUniqueHashtags() {
        assertThat(PlainText.hashtags("Looking for #DFIR and #dfir tips, #Blue_Team, email me@host#notatag, ##double #বাংলা"))
                .containsExactly("dfir", "blue_team", "বাংলা");
        assertThat(PlainText.hashtags("no tags here")).isEmpty();
        String many = java.util.stream.IntStream.range(0, 15).mapToObj(i -> "#t" + i).reduce("", (a, b) -> a + " " + b);
        assertThat(PlainText.hashtags(many)).hasSize(PlainText.MAX_HASHTAGS);
    }

    @Test
    void normalisesTagsForFiltering() {
        assertThat(PlainText.normalizeTag("#DFIR ")).isEqualTo("dfir");
        assertThat(PlainText.normalizeTag("not a tag")).isEmpty();
        assertThat(PlainText.normalizeTag("")).isEmpty();
    }
}
