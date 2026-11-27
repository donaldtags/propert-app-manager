package com.example.estatex.analytics.metrics;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * One reporting period, [start, end) in UTC, aligned to the Harare calendar. A period that hasn't
 * ended yet is <em>incomplete</em>; its figures run to {@link #asOf}, and screens must say so
 * rather than compare it silently with a full period.
 */
public record Period(Grain grain, Instant start, Instant end) {

    public static Period containing(Grain grain, Instant moment, ZoneId zone) {
        ZonedDateTime t = moment.atZone(zone);
        ZonedDateTime s = switch (grain) {
            case HOUR -> t.truncatedTo(ChronoUnit.HOURS);
            case DAY -> t.toLocalDate().atStartOfDay(zone);
            case WEEK -> t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone);
            case MONTH -> t.toLocalDate().withDayOfMonth(1).atStartOfDay(zone);
            case QUARTER -> {
                LocalDate d = t.toLocalDate();
                int firstMonth = ((d.getMonthValue() - 1) / 3) * 3 + 1;
                yield LocalDate.of(d.getYear(), firstMonth, 1).atStartOfDay(zone);
            }
            case YEAR -> LocalDate.of(t.getYear(), 1, 1).atStartOfDay(zone);
        };
        ZonedDateTime e = switch (grain) {
            case HOUR -> s.plusHours(1);
            case DAY -> s.plusDays(1);
            case WEEK -> s.plusWeeks(1);
            case MONTH -> s.plusMonths(1);
            case QUARTER -> s.plusMonths(3);
            case YEAR -> s.plusYears(1);
        };
        return new Period(grain, s.toInstant(), e.toInstant());
    }

    /** Every period of this grain that overlaps [from, to). */
    public static List<Period> overlapping(Grain grain, Instant from, Instant to, ZoneId zone) {
        List<Period> out = new ArrayList<>();
        if (!from.isBefore(to)) {
            out.add(containing(grain, from, zone));
            return out;
        }
        Period p = containing(grain, from, zone);
        while (p.start().isBefore(to)) {
            out.add(p);
            p = containing(grain, p.end(), zone);
        }
        return out;
    }

    /** The period immediately before this one, of the same length. */
    public Period previous(ZoneId zone) {
        return containing(grain, start.minusSeconds(1), zone);
    }

    public boolean isComplete(Instant now) {
        return !end.isAfter(now);
    }

    /** Figures for an incomplete period cover start up to now. */
    public Instant asOf(Instant now) {
        return end.isAfter(now) ? now : end;
    }
}
