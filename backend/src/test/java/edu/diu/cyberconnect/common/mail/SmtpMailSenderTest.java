package edu.diu.cyberconnect.common.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import edu.diu.cyberconnect.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** A relay that rejects a recipient must not break the action that sent the mail (e.g. registration). */
class SmtpMailSenderTest {

    @Test
    void relayFailuresAreLoggedNotThrown() {
        JavaMailSender relay = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 recipient rejected")).when(relay).send(any(SimpleMailMessage.class));
        SmtpMailSender sender = new SmtpMailSender(relay,
                new AppProperties("CyberConnect DIU", "https://example.invalid", "test", "no-reply@example.invalid"));

        assertThatCode(() -> sender.send(new MailMessage("typo@no-such-domain.invalid", "Verify", "body")))
                .doesNotThrowAnyException();
        verify(relay).send(any(SimpleMailMessage.class));
    }
}
