package com.faircourt.gateway.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    /** A clock a test can move forward. */
    private static final class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-18T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    /** Makes {@code times} attempts that all end as failures (the attempt stays counted). */
    private void attempt(String key, int times) {
        for (int i = 0; i < times; i++) {
            throttle.tryAcquire(key);
        }
    }

    @Test
    void fiveAttemptsAreAllowedAndTheNextOneIsLockedForAMinute() {
        for (int i = 0; i < 5; i++) {
            assertThat(throttle.tryAcquire("a@b.c")).as("attempt " + (i + 1)).isEmpty();
        }

        assertThat(throttle.tryAcquire("a@b.c").orElseThrow()).isBetween(60L, 61L);
    }

    @Test
    void theLockEndsAfterItsTime() {
        attempt("a@b.c", 5);

        clock.advance(Duration.ofSeconds(61));

        assertThat(throttle.tryAcquire("a@b.c")).isEmpty();
    }

    @Test
    void everyAttemptAfterALockDoublesTheNextLockUpToFifteenMinutes() {
        attempt("a@b.c", 5);
        clock.advance(Duration.ofSeconds(61));
        assertThat(throttle.tryAcquire("a@b.c")).isEmpty(); // 6th attempt: lock becomes 120 s
        assertThat(throttle.tryAcquire("a@b.c").orElseThrow()).isBetween(119L, 121L);

        clock.advance(Duration.ofSeconds(121));
        assertThat(throttle.tryAcquire("a@b.c")).isEmpty(); // 7th: 240 s
        assertThat(throttle.tryAcquire("a@b.c").orElseThrow()).isBetween(239L, 241L);

        // Keep failing each time the lock ends (always inside the 15 minute window): the lock never passes 15 minutes.
        long longest = 0;
        for (int i = 0; i < 12; i++) {
            long lock = throttle.tryAcquire("a@b.c").orElseThrow();
            longest = Math.max(longest, lock);
            clock.advance(Duration.ofSeconds(lock));
            assertThat(throttle.tryAcquire("a@b.c")).isEmpty();
        }
        assertThat(longest).isBetween(899L, 901L);
    }

    @Test
    void aSuccessClearsTheCount() {
        attempt("a@b.c", 5);

        throttle.recordSuccess("a@b.c");

        for (int i = 0; i < 5; i++) {
            assertThat(throttle.tryAcquire("a@b.c")).isEmpty();
        }
    }

    @Test
    void anAttemptWithoutAVerdictIsGivenBack() {
        for (int i = 0; i < 5; i++) {
            throttle.tryAcquire("a@b.c");
            throttle.release("a@b.c");
        }

        assertThat(throttle.trackedEmails()).isZero();
        for (int i = 0; i < 5; i++) {
            assertThat(throttle.tryAcquire("a@b.c")).isEmpty();
        }
    }

    @Test
    void releasingTheAttemptThatStartedALockLiftsTheLock() {
        attempt("a@b.c", 5); // the 5th attempt starts the lock

        throttle.release("a@b.c");

        assertThat(throttle.tryAcquire("a@b.c")).isEmpty();
    }

    @Test
    void oldAttemptsAreForgotten() {
        attempt("a@b.c", 4);
        clock.advance(Duration.ofMinutes(61));

        for (int i = 0; i < 5; i++) {
            assertThat(throttle.tryAcquire("a@b.c")).as("attempt " + (i + 1)).isEmpty();
        }
    }

    @Test
    void emailsAreCountedSeparatelyAndKeysIgnoreCaseAndSpaces() {
        attempt("a@b.c", 6);

        assertThat(throttle.tryAcquire("other@b.c")).isEmpty();
        assertThat(LoginThrottle.keyOf("  A@B.C ")).isEqualTo("a@b.c");
    }

    @Test
    void parallelGuessesCannotPassTheLimit() throws Exception {
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (throttle.tryAcquire("a@b.c").isEmpty()) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(LoginThrottle.FREE_FAILURES);
    }

    @Test
    void floodingWithRandomEmailsCannotGrowMemoryWithoutLimit() {
        for (int i = 0; i < LoginThrottle.MAX_ENTRIES + 500; i++) {
            throttle.tryAcquire("flood-" + i + "@b.c");
        }

        assertThat(throttle.trackedEmails()).isEqualTo(LoginThrottle.MAX_ENTRIES);
    }
}
