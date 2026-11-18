package com.faircourt.gateway.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing (OWASP Authentication Cheat Sheet: limit attempts, count per account not per IP,
 * lengthen the wait after each failure). Every login attempt for an email is counted <em>before</em> the password is
 * checked ({@link #tryAcquire}), so many parallel guesses cannot slip past the limit. The first {@value #FREE_FAILURES}
 * attempts in a window are allowed; the {@value #FREE_FAILURES}th starts a 60 second lock, and every further attempt
 * after a lock doubles it, up to 15 minutes. A success clears the count; an attempt that got no verdict (the password
 * service was down) is given back ({@link #release}). The email does not have to exist, so the lock reveals nothing
 * about which accounts exist.
 *
 * <p>Kept in memory in the gateway (one instance in the pilot): a restart clears it. At most {@value #MAX_ENTRIES}
 * emails are tracked; the least recently used one is dropped first, so flooding with random emails cannot exhaust
 * memory. Known limits (written in the design): the lock is per email, so someone who knows an email can keep that
 * account locked; one password tried against many emails is not slowed down.
 */
@Component
public class LoginThrottle {

    static final int FREE_FAILURES = 5;
    static final Duration BASE_LOCK = Duration.ofSeconds(60);
    static final Duration MAX_LOCK = Duration.ofMinutes(15);
    /** Attempts older than this are forgotten. Longer than the longest lock, so a maximum lock is not wiped when it ends. */
    static final Duration WINDOW = Duration.ofHours(1);

    static final int MAX_ENTRIES = 10_000;

    /** {@code attempts} counts attempts that have not been cleared by a success. */
    private record State(int attempts, Instant lastAttempt, Instant lockedUntil) {}

    private final Clock clock;
    private final Map<String, State> states = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** The key for an email: trimmed, lower case, cut to the maximum email length. */
    public static String keyOf(String email) {
        String key = email.strip().toLowerCase(Locale.ROOT);
        return key.length() > 254 ? key.substring(0, 254) : key;
    }

    /**
     * Asks to make one login attempt. Empty: allowed, and the attempt is already counted (undo it with
     * {@link #release} or {@link #recordSuccess}). Not empty: locked, and the value is the seconds to wait.
     */
    public synchronized OptionalLong tryAcquire(String key) {
        Instant now = clock.instant();
        State previous = states.get(key);
        if (previous != null
                && previous.lockedUntil() != null
                && previous.lockedUntil().isAfter(now)) {
            return OptionalLong.of(
                    Math.max(1, Duration.between(now, previous.lockedUntil()).toSeconds() + 1));
        }
        int attempts = 1;
        if (previous != null && previous.lastAttempt().plus(WINDOW).isAfter(now)) {
            attempts = previous.attempts() + 1;
        }
        states.put(key, new State(attempts, now, lockUntil(attempts, now)));
        return OptionalLong.empty();
    }

    /** The attempt ended without a verdict on the password (service down): give it back. */
    public synchronized void release(String key) {
        State state = states.get(key);
        if (state == null) {
            return;
        }
        int attempts = state.attempts() - 1;
        if (attempts <= 0) {
            states.remove(key);
        } else {
            states.put(key, new State(attempts, state.lastAttempt(), lockUntil(attempts, state.lastAttempt())));
        }
    }

    public synchronized void recordSuccess(String key) {
        states.remove(key);
    }

    private static Instant lockUntil(int attempts, Instant from) {
        if (attempts < FREE_FAILURES) {
            return null;
        }
        long doublings = Math.min(attempts - FREE_FAILURES, 10);
        Duration lock = BASE_LOCK.multipliedBy(1L << doublings);
        return from.plus(lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock);
    }

    synchronized int trackedEmails() {
        return states.size();
    }
}
