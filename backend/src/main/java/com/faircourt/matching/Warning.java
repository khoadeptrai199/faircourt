package com.faircourt.matching;

/** Per-court warnings shown to the host (blueprint section 14). */
public enum Warning {
    /** Band spread (max - min) on the court is 2 or more. */
    WIDE_SKILL_GAP,
    /** At least one player has no band; counted as band 2 for grouping only. */
    UNKNOWN_LEVEL
}
