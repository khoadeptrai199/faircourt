package com.faircourt.auth.passwordreset;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The one place that sends mail from auth-service (Password reset design D-6). Mails go out on a separate thread after
 * the database commit, so a slow mail server cannot change how long the request takes or whether it succeeded. A
 * failure is logged without any content (no code, no address) and never reaches the caller. Plain text only, no links.
 */
@Component
public class ResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(ResetMailSender.class);
    private static final DateTimeFormatter VIETNAM_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;
    private final String from;
    private final Executor executor;

    @Autowired
    public ResetMailSender(JavaMailSender mailSender, @Value("${faircourt.mail.from}") String from) {
        this(mailSender, from, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** For tests: choose the executor (for example one that runs in the calling thread). */
    public ResetMailSender(JavaMailSender mailSender, String from, Executor executor) {
        this.mailSender = mailSender;
        this.from = from;
        this.executor = executor;
    }

    public void sendCode(String to, String code) {
        send(
                to,
                "Your FairCourt code",
                "Your FairCourt code is " + code + ". It is valid for 10 minutes.\n\n"
                        + "If you did not ask for it, ignore this mail.");
    }

    public void sendPasswordChanged(String to, Instant changedAt) {
        send(
                to,
                "Your FairCourt password was changed",
                "Your FairCourt password was changed at " + VIETNAM_TIME.format(changedAt) + " (Vietnam time).\n\n"
                        + "If this was not you, tell your host.");
    }

    private void send(String to, String subject, String text) {
        executor.execute(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
            } catch (RuntimeException e) {
                // Never log the address or the text: they hold the code.
                log.error("Could not send a password mail: {}", e.getClass().getSimpleName());
            }
        });
    }
}
