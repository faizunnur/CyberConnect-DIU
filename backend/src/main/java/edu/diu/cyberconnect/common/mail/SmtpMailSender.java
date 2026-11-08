package edu.diu.cyberconnect.common.mail;

import edu.diu.cyberconnect.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends plain-text transactional mail. A relay failure (unreachable server, rejected recipient such as a mistyped
 * domain) is logged and swallowed: the action that triggered the mail (registration, password reset, a decision) has
 * already succeeded and must not fail or roll back because of it. Members can request verification mail again.
 */
@Component
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final AppProperties app;

    public SmtpMailSender(JavaMailSender javaMailSender, AppProperties app) {
        this.javaMailSender = javaMailSender;
        this.app = app;
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(app.mailFrom());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.textBody());
        try {
            javaMailSender.send(mail);
            log.info("Email sent: subject='{}' recipientDomain='{}'", message.subject(), domainOf(message.to()));
        } catch (MailException e) {
            log.warn("Email NOT sent: subject='{}' recipientDomain='{}' reason={}", message.subject(), domainOf(message.to()),
                    e.getClass().getSimpleName());
        }
    }

    private static String domainOf(String address) {
        int at = address == null ? -1 : address.lastIndexOf('@');
        return at < 0 ? "unknown" : address.substring(at + 1);
    }
}
