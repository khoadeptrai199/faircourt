package com.faircourt.auth;

import com.faircourt.auth.passwordreset.ResetMailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Test doubles for the password reset: mail is recorded instead of sent (and sent in the calling thread, so a test can
 * look at it at once), and the clock can be moved forward.
 */
@TestConfiguration
class TestMailConfig {

    /** Records every mail; can be told to fail like a broken mail server. */
    static final class RecordingMailSender extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void send(SimpleMailMessage... messages) {
            if (failing) {
                throw new MailSendException("mail server down (test)");
            }
            sent.addAll(List.of(messages));
        }

        void clear() {
            sent.clear();
            failing = false;
        }
    }

    /** A clock a test can move forward; starts at the real current time so it agrees with the database. */
    static final class MovableClock extends Clock {
        private volatile Instant now = Instant.now();

        void advance(Duration d) {
            now = now.plus(d);
        }

        /** Back to the real current time (each test starts from it, so the clock agrees with the database). */
        void sync() {
            now = Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Bean
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }

    @Bean
    @Primary
    ResetMailSender testResetMailSender(RecordingMailSender recording) {
        return new ResetMailSender(recording, "no-reply@test.local", Runnable::run);
    }

    @Bean
    @Primary
    MovableClock movableClock() {
        return new MovableClock();
    }
}
