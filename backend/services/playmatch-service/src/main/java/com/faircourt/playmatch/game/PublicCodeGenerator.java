package com.faircourt.playmatch.game;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Random shareable public code for a game (the play event a host posts) (blueprint sections 11 and 13, e.g. "FRI7Q2K9").
 * 8 characters from an alphabet without look-alike characters (0/O, 1/I).
 * The public code only opens the read-only summary; it never authenticates anyone.
 */
@Component
public class PublicCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;

    private final SecureRandom random;

    /** The Spring bean used by the create-game command. */
    @Autowired
    public PublicCodeGenerator() {
        this(new SecureRandom());
    }

    public PublicCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String next() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
