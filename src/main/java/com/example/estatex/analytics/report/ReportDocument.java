package com.example.estatex.analytics.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A generated report, independent of its file format. Every report carries the date it was
 * generated, the period it covers, the filters applied, what data it is built from (coverage) and
 * how the figures are worked out (methodology), so a file read months later still explains itself.
 */
public record ReportDocument(ReportType type, String title, String subtitle, Instant generatedAt, String generatedBy,
                             LocalDate from, LocalDate to, String periodLabel, String filtersLabel, List<Section> sections,
                             List<String> coverage, List<String> methodology, List<String> notes) {

    public enum Unit { TEXT, COUNT, NUMBER, USD, PERCENT, DATE }

    public record Column(String label, Unit unit) {
        public static Column text(String label) {
            return new Column(label, Unit.TEXT);
        }

        public static Column count(String label) {
            return new Column(label, Unit.COUNT);
        }

        public static Column usd(String label) {
            return new Column(label, Unit.USD);
        }

        public static Column pct(String label) {
            return new Column(label, Unit.PERCENT);
        }

        public static Column num(String label) {
            return new Column(label, Unit.NUMBER);
        }
    }

    /** One table. Cells are String, Number, LocalDate, Instant or null (no data, never zero). */
    public record Section(String title, String subtitle, List<Column> columns, List<List<Object>> rows, String emptyMessage) {
        public Section {
            for (List<Object> row : rows) {
                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException("Row width " + row.size() + " != " + columns.size() + " in " + title);
                }
            }
        }
    }
}
