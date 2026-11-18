package com.faircourt.player.guest;

/**
 * Provisional skill band derived from the four self-reported answers (blueprint section 13): total 0-3 is band 1,
 * 4-6 band 2, 7-9 band 3, 10-12 band 4. A pilot heuristic, not a rating.
 */
public enum AnswerBand {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4);

    private final int value;

    AnswerBand(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** @param total sum of the four answers, 0-12 */
    public static AnswerBand forTotal(int total) {
        if (total < 0 || total > 12) {
            throw new IllegalArgumentException("Total of the answers must be 0-12");
        }
        if (total <= 3) {
            return ONE;
        }
        if (total <= 6) {
            return TWO;
        }
        if (total <= 9) {
            return THREE;
        }
        return FOUR;
    }
}
