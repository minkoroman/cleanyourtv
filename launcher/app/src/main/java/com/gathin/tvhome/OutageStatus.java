package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * What the Home strip says for one queue at one moment: light or not, until when, and what tomorrow holds.
 * Switching time (MAYBE) counts as light, like the Svitlo app and the bot. Pure, for JVM tests.
 */
final class OutageStatus {

    /** The site says no outages today. */
    boolean noOutages;
    /** An outage now. */
    boolean off;
    /** When the light goes (or comes back); MIDNIGHT for the end of the day; null when nothing changes. */
    LocalTime until;
    /** until is tomorrow's first outage. */
    boolean untilTomorrow;
    boolean tomorrowKnown;
    int tomorrowOutages;
    LocalTime tomorrowFirst;
    /** Today's 48 half hours, for the bar; empty when cancelled. */
    List<PoeSchedule.Slot> today;
    String updated;

    static OutageStatus at(Map<LocalDate, PoeSchedule.Day> days, String queue, LocalDate date, LocalTime now) {
        PoeSchedule.Day day = days.get(date);
        if (day == null) return null;
        OutageStatus s = new OutageStatus();
        s.updated = day.updated;
        PoeSchedule.Day next = days.get(date.plusDays(1));
        List<PoeSchedule.Slot> nextRow = next == null || next.cancelled ? null : next.rows.get(queue);
        s.tomorrowKnown = next != null && (next.cancelled || nextRow != null);
        if (nextRow != null) {
            s.tomorrowOutages = outages(nextRow);
            s.tomorrowFirst = firstOff(nextRow);
        }
        if (day.cancelled) {
            s.noOutages = true;
            s.today = java.util.Collections.emptyList();
            return s;
        }
        List<PoeSchedule.Slot> row = day.rows.get(queue);
        if (row == null) return null;
        s.today = row;
        int slot = now.getHour() * 2 + now.getMinute() / 30;
        s.off = row.get(slot) == PoeSchedule.Slot.OFF;
        int j = slot + 1;
        while (j < PoeSchedule.SLOTS && (row.get(j) == PoeSchedule.Slot.OFF) == s.off) j++;
        if (j < PoeSchedule.SLOTS) {
            s.until = time(j);
        } else if (s.off) {
            s.until = LocalTime.MIDNIGHT;   // runs to the end of the day; tomorrow's table, if any, says the rest
            if (nextRow != null && nextRow.get(0) == PoeSchedule.Slot.OFF) {
                int k = 0;
                while (k < PoeSchedule.SLOTS && nextRow.get(k) == PoeSchedule.Slot.OFF) k++;
                s.until = time(k);
                s.untilTomorrow = true;
            }
        } else if (s.tomorrowFirst != null) {
            s.until = s.tomorrowFirst;
            s.untilTomorrow = true;
        }
        return s;
    }

    /** Separate outage blocks in a day. */
    static int outages(List<PoeSchedule.Slot> row) {
        int n = 0;
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i) == PoeSchedule.Slot.OFF && (i == 0 || row.get(i - 1) != PoeSchedule.Slot.OFF)) n++;
        }
        return n;
    }

    static LocalTime firstOff(List<PoeSchedule.Slot> row) {
        for (int i = 0; i < row.size(); i++) if (row.get(i) == PoeSchedule.Slot.OFF) return time(i);
        return null;
    }

    private static LocalTime time(int slot) {
        return slot >= PoeSchedule.SLOTS ? LocalTime.MIDNIGHT : LocalTime.of(slot / 2, (slot % 2) * 30);
    }
}
