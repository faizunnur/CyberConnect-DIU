package edu.diu.cyberconnect.common.mail;

/** Port for outbound email. Implementations must never log message bodies containing tokens. */
public interface MailSender {

    void send(MailMessage message);
}
