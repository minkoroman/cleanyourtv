package com.gathin.tvhome;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Poltavaoblenergo's hourly outage schedule (poe.pl.ua/customs/dynamicgpv-info.php): 12 queues x 48 half hours
 * per day, today and, once published, tomorrow. A Java port of the parser in the Svitlo app's core (PoeParser.kt):
 * every table on its own, the month name in any case, a clock-change day (50 or 46 cells) folded to 48 slots.
 */
final class PoeSchedule {

    static final String URL = "https://www.poe.pl.ua/customs/dynamicgpv-info.php";
    static final int SLOTS = 48;

    /** A half hour: light (light_1), outage (light_2), or switching time when light may be off (light_3). */
    enum Slot { ON, OFF, MAYBE }

    static final class Day {
        final LocalDate date;
        /** "23 жовтня 2026 07:42", as the site prints it; may be empty. */
        final String updated;
        final Map<String, List<Slot>> rows;
        /** The site says no outages are expected that day. */
        final boolean cancelled;

        Day(LocalDate date, String updated, Map<String, List<Slot>> rows, boolean cancelled) {
            this.date = date;
            this.updated = updated;
            this.rows = rows;
            this.cancelled = cancelled;
        }
    }

    private static final Map<String, Integer> MONTHS = new HashMap<>();
    static {
        String[] names = {"січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня",
                "жовтня", "листопада", "грудня"};
        for (int i = 0; i < names.length; i++) MONTHS.put(names[i], i + 1);
    }

    private static final int IU = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\s+([а-яіїєґ]+)\\s+(\\d{4})\\s+року", IU);
    private static final Pattern STAMP = Pattern.compile("\\d{1,2}\\s+[а-яіїєґ]+\\s+\\d{4}\\s+\\d{2}:\\d{2}", IU);
    private static final Pattern CANCEL = Pattern.compile("(\\d{1,2})\\s+([а-яіїєґ]+)\\s+(\\d{4})\\s+року\\s+"
            + "застосування\\s+графіка\\s+погодинного\\s+відключення[^.]{0,150}?не\\s+прогнозується", IU);
    private static final Pattern TABLE_OPEN = Pattern.compile("<table\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE_CLOSE = Pattern.compile("</table\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TBODY = Pattern.compile("<tbody\\b[^>]*>(.*?)</tbody\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TR = Pattern.compile("<tr\\b[^>]*>(.*?)(?=<tr\\b|</tbody|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TD = Pattern.compile("<td\\b([^>]*)>([^<]*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_STYLE = Pattern.compile("<(script|style)\\b.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private PoeSchedule() {}

    /** Every day the page describes, by date. Throws IllegalArgumentException when it describes none. */
    static Map<LocalDate, Day> parse(String html) {
        Map<LocalDate, Day> days = new LinkedHashMap<>();
        List<int[]> tables = tables(html);
        for (int i = 0; i < tables.size(); i++) {
            int[] t = tables.get(i);
            LocalDate date = lastDate(plain(html.substring(0, t[0])));
            if (date == null) continue;   // a broken table: the other day may still be fine
            int afterEnd = i + 1 < tables.size() ? tables.get(i + 1)[0] : html.length();
            Matcher stamp = STAMP.matcher(plain(html.substring(t[1], afterEnd)));
            Map<String, List<Slot>> rows = rows(html.substring(t[2], t[3]));
            if (rows.isEmpty()) continue;
            days.put(date, new Day(date, stamp.find() ? stamp.group() : "", rows, false));
        }
        String text = plain(html);
        Matcher c = CANCEL.matcher(text);
        while (c.find()) {
            LocalDate date = date(c.group(1), c.group(2), c.group(3));
            if (date == null || days.containsKey(date)) continue;   // a table wins over the message
            Matcher stamp = STAMP.matcher(text.substring(c.end(), Math.min(text.length(), c.end() + 80)));
            days.put(date, new Day(date, stamp.find() ? stamp.group() : "", Collections.emptyMap(), true));
        }
        if (days.isEmpty()) throw new IllegalArgumentException("no schedule tables and no cancellation notice");
        return new TreeMap<>(days);
    }

    private static LocalDate lastDate(String text) {
        Matcher m = DATE.matcher(text);
        LocalDate found = null;
        while (m.find()) found = date(m.group(1), m.group(2), m.group(3));
        return found;
    }

    /** {start, end, bodyStart, bodyEnd} of every schedule table. */
    private static List<int[]> tables(String html) {
        List<int[]> out = new ArrayList<>();
        Matcher open = TABLE_OPEN.matcher(html);
        Matcher close = TABLE_CLOSE.matcher(html);
        int from = 0;
        while (open.find(from)) {
            if (!close.find(open.end())) break;
            if (classes(open.group(1)).contains("turnoff-scheduleui-table")) {
                out.add(new int[]{open.start(), close.end(), open.end(), close.start()});
            }
            from = close.end();
        }
        return out;
    }

    private static Map<String, List<Slot>> rows(String table) {
        Matcher body = TBODY.matcher(table);
        String rowsHtml = body.find() ? body.group(1) : table;
        Map<String, List<Slot>> rows = new LinkedHashMap<>();
        String queue = null;
        Matcher tr = TR.matcher(rowsHtml);
        while (tr.find()) {
            String sub = null;
            List<Slot> states = new ArrayList<>();
            Matcher td = TD.matcher(tr.group(1));
            while (td.find()) {
                Set<String> cls = classes(td.group(1));
                if (cls.contains("turnoff-scheduleui-table-queue")) {
                    Matcher n = NUMBER.matcher(td.group(2));
                    if (n.find()) queue = n.group();
                } else if (cls.contains("turnoff-scheduleui-table-subqueue")) {
                    sub = td.group(2).replaceAll("(?i)&nbsp;?|\u00A0", " ").trim();
                } else if (cls.contains("light_1")) {
                    states.add(Slot.ON);
                } else if (cls.contains("light_2")) {
                    states.add(Slot.OFF);
                } else if (cls.contains("light_3")) {
                    states.add(Slot.MAYBE);
                }
            }
            if (sub == null || queue == null) continue;
            List<Slot> wall = wallClock(states);
            if (wall != null) rows.put(queue + "." + sub, wall);
        }
        return rows;
    }

    /** Autumn's doubled 03:00-04:00 (50 cells) and spring's missing hour (46) folded to 48 wall-clock slots. */
    static List<Slot> wallClock(List<Slot> s) {
        if (s.size() == SLOTS) return s;
        List<Slot> out = new ArrayList<>(s);
        if (s.size() == SLOTS + 2) {
            out.set(6, worst(out.get(6), out.get(8)));
            out.set(7, worst(out.get(7), out.get(9)));
            out.remove(9);
            out.remove(8);
            return out;
        }
        if (s.size() == SLOTS - 2) {
            out.add(6, out.get(5));
            out.add(7, out.get(5));
            return out;
        }
        return null;
    }

    /** The darker of two half hours: an outage over switching time over light. */
    private static Slot worst(Slot a, Slot b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(Slot s) {
        return s == Slot.OFF ? 2 : s == Slot.MAYBE ? 1 : 0;
    }

    private static LocalDate date(String day, String month, String year) {
        Integer m = MONTHS.get(month.toLowerCase(java.util.Locale.ROOT));
        if (m == null) return null;
        try {
            return LocalDate.of(Integer.parseInt(year), m, Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static Set<String> classes(String attrs) {
        Matcher m = CLASS.matcher(attrs);
        Set<String> out = new LinkedHashSet<>();
        if (m.find()) for (String c : m.group(1).trim().split("\\s+")) if (!c.isEmpty()) out.add(c);
        return out;
    }

    static String plain(String html) {
        return SCRIPT_STYLE.matcher(html).replaceAll(" ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("(?i)&nbsp;?|\u00A0", " ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ");
    }
}
