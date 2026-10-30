package edu.diu.cyberconnect.common.mail;

public record MailMessage(String to, String subject, String textBody) {
}
