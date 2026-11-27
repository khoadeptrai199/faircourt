package com.faircourt.playmatch.attendance;

import static com.faircourt.playmatch.attendance.AttendanceState.ASSIGNED;
import static com.faircourt.playmatch.attendance.AttendanceState.LEFT;
import static com.faircourt.playmatch.attendance.AttendanceState.PENDING;
import static com.faircourt.playmatch.attendance.AttendanceState.PLAYING;
import static com.faircourt.playmatch.attendance.AttendanceState.READY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttendanceStateTest {

    @Test
    void hostTargetsAreExactlyReadyAndLeft() {
        Set<AttendanceState> targets = EnumSet.noneOf(AttendanceState.class);
        for (AttendanceState state : AttendanceState.values()) {
            if (state.isHostTarget()) {
                targets.add(state);
            }
        }

        assertThat(targets).containsExactlyInAnyOrder(READY, LEFT);
    }

    @Test
    void hostMovesAreExactlyPendingToReadyPendingToLeftAndReadyToLeft() {
        Set<String> allowed = new java.util.TreeSet<>();
        for (AttendanceState from : AttendanceState.values()) {
            for (AttendanceState to : AttendanceState.values()) {
                if (from.hostMayMoveTo(to)) {
                    allowed.add(from + "->" + to);
                }
            }
        }

        assertThat(allowed).containsExactlyInAnyOrder("PENDING->READY", "PENDING->LEFT", "READY->LEFT");
    }

    @Test
    void onlyReadyCanBeAssigned() {
        for (AttendanceState state : AttendanceState.values()) {
            assertThat(state.canBeAssigned()).as(state.name()).isEqualTo(state == READY);
        }
    }

    @Test
    void onlyAssignedAndPlayingAreInAMatch() {
        for (AttendanceState state : AttendanceState.values()) {
            assertThat(state.inMatch()).as(state.name()).isEqualTo(state == ASSIGNED || state == PLAYING);
        }
        assertThat(PENDING.inMatch()).isFalse();
    }
}
