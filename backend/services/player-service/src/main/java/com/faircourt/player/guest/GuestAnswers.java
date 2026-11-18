package com.faircourt.player.guest;

import java.util.Optional;

/**
 * The four self-reported answers of a guest (blueprint sections 4, 13): each 0-3. "All four scored or all four
 * unknown": there is no partial set. An unknown set is represented by {@link Optional#empty()} from {@link #parse}.
 */
public record GuestAnswers(int serve, int rally, int movement, int positioning) {

    public GuestAnswers {
        for (int value : new int[] {serve, rally, movement, positioning}) {
            if (value < 0 || value > 3) {
                throw new IllegalArgumentException("Each answer must be 0-3");
            }
        }
    }

    /**
     * @return the answers, or empty when all four are {@code null} (unknown level)
     * @throws IllegalArgumentException for a partial set or a value outside 0-3
     */
    public static Optional<GuestAnswers> parse(Integer serve, Integer rally, Integer movement, Integer positioning) {
        boolean allUnknown = serve == null && rally == null && movement == null && positioning == null;
        if (allUnknown) {
            return Optional.empty();
        }
        if (serve == null || rally == null || movement == null || positioning == null) {
            throw new IllegalArgumentException("Answer all four questions or none");
        }
        return Optional.of(new GuestAnswers(serve, rally, movement, positioning));
    }

    public int total() {
        return serve + rally + movement + positioning;
    }

    public AnswerBand band() {
        return AnswerBand.forTotal(total());
    }

    /** JSON stored in {@code skill_assessment.answers}; built from integers only, never from user text. */
    public String toJson() {
        return "{\"serve\":" + serve + ",\"rally\":" + rally + ",\"movement\":" + movement + ",\"positioning\":"
                + positioning + "}";
    }
}
