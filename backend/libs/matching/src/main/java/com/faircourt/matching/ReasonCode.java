package com.faircourt.matching;

/**
 * Rule-based explanation tags (codes from blueprint section 13). Trigger rules are an implementation
 * interpretation, documented in README "Decisions for owner review":
 * <ul>
 * <li>SIMILAR_LEVEL: band spread on the court is at most 1.</li>
 * <li>BALANCED_TEAMS: team band sums are equal.</li>
 * <li>LONG_WAIT: the court includes the earliest-available ready player(s), and not every ready player
 * became available at the same instant.</li>
 * </ul>
 */
public enum ReasonCode {
    SIMILAR_LEVEL,
    BALANCED_TEAMS,
    LONG_WAIT
}
