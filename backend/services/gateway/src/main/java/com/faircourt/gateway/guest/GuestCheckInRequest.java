package com.faircourt.gateway.guest;

/**
 * Body of {@code POST /api/v1/public/games/{publicCode}/guests} (blueprint section 13). Checked by hand in
 * {@link GuestController} (no extra validation dependency in the gateway).
 */
public record GuestCheckInRequest(String displayName, Answers answers) {

    /** The four observable-play answers, each 0-3; all four or none (blueprint section 13). */
    public record Answers(Integer serve, Integer rally, Integer movement, Integer positioning) {}
}
