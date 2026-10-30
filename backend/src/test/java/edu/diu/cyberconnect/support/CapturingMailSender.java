package edu.diu.cyberconnect.support;

import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test double: keeps every outbound mail so tests can read verification and reset links. */
public class CapturingMailSender implements MailSender {

    private final List<MailMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        messages.add(message);
    }

    public List<MailMessage> all() {
        return List.copyOf(messages);
    }

    public Optional<MailMessage> lastTo(String email) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).to().equalsIgnoreCase(email)) {
                return Optional.of(messages.get(i));
            }
        }
        return Optional.empty();
    }

    /** Extracts the value of a query parameter from the first link in the message. */
    public String extractParam(MailMessage message, String param) {
        Matcher matcher = Pattern.compile("[?&]" + param + "=([A-Za-z0-9_\\-]+)").matcher(message.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No '" + param + "' parameter in mail: " + message.subject());
        }
        return matcher.group(1);
    }

    public void clear() {
        messages.clear();
    }
}
