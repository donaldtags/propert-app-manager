package com.example.estatex.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.estatex.analytics.report.ReportDocument.Column;
import com.example.estatex.analytics.report.ReportDocument.Section;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ReportRendererTest {
    private static final ZoneId HARARE = ZoneId.of("Africa/Harare");

    static ReportDocument doc() {
        Section figures = new Section("Key figures", "Asking vs agreed, never added together.",
                List.of(Column.text("Measure"), Column.usd("Value"), Column.pct("Change"), Column.count("n")),
                List.of(Arrays.asList("Median asking rent", new BigDecimal("750.00"), new BigDecimal("-5.0"), 42),
                        Arrays.asList("=HYPERLINK(\"http://evil\")", null, null, 0),
                        Arrays.asList("Tags <b> & \"quotes\" − 2×", new BigDecimal("1234567.891"), new BigDecimal("12.34"), 7)),
                null);
        Section empty = new Section("By suburb: [Harare]/*?", null, List.of(Column.text("Suburb"), Column.count("Active")), List.of(),
                "No listings match.");
        return new ReportDocument(ReportType.WEEKLY_RENTAL, "Weekly rental market report", null, Instant.parse("2026-11-26T08:30:00Z"),
                "Test Admin", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), "16 Nov 2026 to 22 Nov 2026", "City: Harare",
                List.of(figures, empty), List.of("Listing figures from the snapshot of 2026-11-22."),
                List.of("Prices are medians; fewer than 5 is low sample."), List.of("A note."));
    }

    @Test
    void csvCarriesTheContextAndDefusesFormulas() {
        String csv = new String(ReportRenderer.render(doc(), ReportFormat.CSV, HARARE), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Weekly rental market report");
        assertThat(csv).contains("Generated,\"26 Nov 2026, 10:30 (Africa/Harare)\",by Test Admin");
        assertThat(csv).contains("Period,16 Nov 2026 to 22 Nov 2026,2026-11-16,2026-11-22");
        assertThat(csv).contains("Measure,Value (USD),Change (%),n");
        assertThat(csv).contains("Median asking rent,750,-5,42");                         // a negative number is not defused
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\",,,0");              // a formula is
        assertThat(csv).contains("No listings match.");
        assertThat(csv).contains("Data coverage\r\nListing figures from the snapshot of 2026-11-22.");
        assertThat(csv).contains("Methodology\r\nPrices are medians");
        assertThat(ReportRenderer.csvCell("+1")).isEqualTo("'+1");
        assertThat(ReportRenderer.csvCell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(ReportRenderer.csvCell(new BigDecimal("-3"))).isEqualTo("-3");
    }

    @Test
    void xlsxIsAWellFormedWorkbookWithNumericCells() throws Exception {
        Map<String, String> parts = unzip(ReportRenderer.render(doc(), ReportFormat.XLSX, HARARE));
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/worksheets/sheet3.xml");
        var factory = DocumentBuilderFactory.newInstance();
        for (Map.Entry<String, String> p : parts.entrySet()) {
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(p.getValue().getBytes(StandardCharsets.UTF_8)));   // throws if malformed
        }
        assertThat(parts.get("xl/workbook.xml")).contains("name=\"Summary\"", "name=\"Key figures\"", "name=\"By suburb   Harare\"");
        String figures = parts.get("xl/worksheets/sheet2.xml");
        assertThat(figures).contains("<c r=\"B2\" s=\"3\"><v>750</v></c>", "<c r=\"C2\" s=\"4\"><v>-5</v></c>", "<v>1234567.891</v>");
        assertThat(figures).contains("Tags &lt;b&gt; &amp; &quot;quotes&quot; − 2×");
        assertThat(figures).contains("<pane ySplit=\"1\"");
        assertThat(parts.get("xl/worksheets/sheet1.xml")).contains("Data coverage", "Methodology", "16 Nov 2026 to 22 Nov 2026");
        assertThat(parts.get("xl/worksheets/sheet3.xml")).contains("No listings match.");
    }

    @Test
    void pdfRendersTheTablesAndNotes() throws Exception {
        byte[] pdf = ReportRenderer.render(doc(), ReportFormat.PDF, HARARE);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument d = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(d);
            assertThat(text).contains("Weekly rental market report", "Median asking rent", "$750.00", "-5%", "$1,234,568",
                    "No listings match.", "Data coverage", "Methodology", "Generated", "Page 1 of");
            assertThat(text).contains("- 2x");   // symbols Helvetica lacks are replaced, not dropped
        }
    }

    @Test
    void helpers() {
        assertThat(ReportRenderer.ref(1, 1)).isEqualTo("A1");
        assertThat(ReportRenderer.ref(26, 3)).isEqualTo("Z3");
        assertThat(ReportRenderer.ref(27, 1)).isEqualTo("AA1");
        assertThat(ReportRenderer.ref(53, 1)).isEqualTo("BA1");
        assertThat(ReportRenderer.display(null, ReportDocument.Unit.USD, HARARE)).isEqualTo("-");
        assertThat(ReportRenderer.display(new BigDecimal("-12.5"), ReportDocument.Unit.USD, HARARE)).isEqualTo("-$12.50");
        assertThat(ReportRenderer.display(new BigDecimal("12.345"), ReportDocument.Unit.PERCENT, HARARE)).isEqualTo("12.3%");
        assertThat(ReportRenderer.fileName(doc(), ReportFormat.XLSX)).isEqualTo("estatex-weekly-rental-2026-11-16_to_2026-11-22.xlsx");
        assertThat(ReportBuilder.humanise("askingMedian")).isEqualTo("Asking median");
        assertThat(ReportBuilder.humanise("newMrr")).isEqualTo("New MRR");
    }

    static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) out.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }
}
