package legalvideo;

import java.time.LocalDate;

public final class MatterSessionServiceTest {
    public static void main(String[] args) {
        MatterSessionService.FollowUp dueSoon = MatterSessionService.followUp(
            LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1));
        assertEquals("DUE_SOON", dueSoon.state());
        assertEquals("Confirm filing readiness today", dueSoon.action());
        assertEquals(2L, dueSoon.daysRemaining());

        MatterSessionService.FollowUp planned = MatterSessionService.followUp(
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 1));
        assertEquals("PLANNED", planned.state());
        assertEquals(11L, planned.daysRemaining());
        System.out.println("Matter deadline decisions passed");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
