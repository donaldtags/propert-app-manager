package com.example.estatex.analytics.report;

import com.example.estatex.analytics.report.ReportDocument.Column;
import com.example.estatex.analytics.report.ReportDocument.Section;
import com.example.estatex.analytics.report.ReportDocument.Unit;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a {@link ReportDocument} as PDF, Excel (.xlsx) or CSV. No spreadsheet library: an .xlsx
 * is a zip of a few XML parts, written here directly, with real numeric cells so totals and sorting
 * work in Excel.
 */
public final class ReportRenderer {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK);

    private ReportRenderer() {
    }

    public static byte[] render(ReportDocument doc, ReportFormat format, ZoneId zone) {
        return switch (format) {
            case CSV -> csv(doc, zone);
            case XLSX -> xlsx(doc, zone);
            case PDF -> pdf(doc, zone);
        };
    }

    public static String fileName(ReportDocument doc, ReportFormat format) {
        String period = doc.from().equals(doc.to()) ? doc.from().toString() : doc.from() + "_to_" + doc.to();
        return "estatex-" + doc.type().name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + period + "." + format.extension();
    }

    // ---- CSV -----------------------------------------------------------------------------------

    static byte[] csv(ReportDocument doc, ZoneId zone) {
        StringBuilder s = new StringBuilder("﻿");   // BOM, so Excel opens it as UTF-8
        line(s, List.of(doc.title()));
        line(s, List.of("Generated", stamp(doc.generatedAt(), zone), "by " + doc.generatedBy()));
        line(s, List.of("Period", doc.periodLabel(), doc.from().toString(), doc.to().toString()));
        line(s, List.of("Filters", doc.filtersLabel()));
        s.append("\r\n");
        for (Section sec : doc.sections()) {
            line(s, List.of(sec.title()));
            if (sec.subtitle() != null) line(s, List.of(sec.subtitle()));
            if (sec.rows().isEmpty()) {
                line(s, List.of(sec.emptyMessage() == null ? "No data for this period." : sec.emptyMessage()));
            } else {
                line(s, sec.columns().stream().map(c -> (Object) header(c)).toList());
                for (List<Object> row : sec.rows()) {
                    List<Object> cells = new ArrayList<>();
                    for (Object v : row) cells.add(v instanceof Number n ? new BigDecimal(plain(n)) : v == null ? "" : text(v, zone));
                    line(s, cells);
                }
            }
            s.append("\r\n");
        }
        block(s, "Data coverage", doc.coverage());
        block(s, "Methodology", doc.methodology());
        block(s, "Notes", doc.notes());
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void block(StringBuilder s, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        line(s, List.of(title));
        for (String l : lines) line(s, List.of(l));
        s.append("\r\n");
    }

    private static void line(StringBuilder s, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) s.append(',');
            s.append(csvCell(cells.get(i)));
        }
        s.append("\r\n");
    }

    /** Quotes when needed, and defuses text a spreadsheet would run as a formula (CSV injection). */
    static String csvCell(Object v) {
        if (v == null) return "";
        if (v instanceof Number n) return plain(n);   // numbers are never formulas, even when negative
        String t = v.toString();
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        return t.matches("(?s).*[\",\r\n].*") ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }

    // ---- XLSX ----------------------------------------------------------------------------------

    static byte[] xlsx(ReportDocument doc, ZoneId zone) {
        List<String> names = new ArrayList<>();
        List<String> sheets = new ArrayList<>();
        Set<String> used = new HashSet<>();
        names.add(sheetName("Summary", used));
        sheets.add(summarySheet(doc, zone));
        for (Section sec : doc.sections()) {
            names.add(sheetName(sec.title(), used));
            sheets.add(sectionSheet(sec, zone));
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            StringBuilder types = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
            for (int i = 1; i <= sheets.size(); i++) {
                types.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                        .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            }
            types.append("</Types>");
            put(zip, "[Content_Types].xml", types.toString());
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                    + "</Relationships>");
            put(zip, "docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                    + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>" + xml(doc.title()) + "</dc:title>"
                    + "<dc:creator>EstateX</dc:creator><dcterms:created xsi:type=\"dcterms:W3CDTF\">" + doc.generatedAt()
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS) + "</dcterms:created></cp:coreProperties>");
            StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 1; i <= sheets.size(); i++) {
                wb.append("<sheet name=\"").append(xml(names.get(i - 1))).append("\" sheetId=\"").append(i)
                        .append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                        .append(i).append(".xml\"/>");
            }
            wb.append("</sheets></workbook>");
            rels.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                    .append("</Relationships>");
            put(zip, "xl/workbook.xml", wb.toString());
            put(zip, "xl/_rels/workbook.xml.rels", rels.toString());
            put(zip, "xl/styles.xml", STYLES);
            for (int i = 1; i <= sheets.size(); i++) put(zip, "xl/worksheets/sheet" + i + ".xml", sheets.get(i - 1));
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the spreadsheet", e);
        }
    }

    /** Styles: 0 normal, 1 bold, 2 whole number, 3 money (2 dp), 4 one decimal, 5 bold title. */
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.0\"/></numFmts>"
            + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"6\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

    private static String summarySheet(ReportDocument doc, ZoneId zone) {
        List<String> rows = new ArrayList<>();
        int r = 1;
        rows.add(row(r++, List.of(str(1, r - 1, doc.title(), 5))));
        if (doc.subtitle() != null) rows.add(row(r++, List.of(str(1, r - 1, doc.subtitle(), 0))));
        r++;
        for (String[] kv : new String[][]{{"Generated", stamp(doc.generatedAt(), zone) + " by " + doc.generatedBy()},
                {"Period", doc.periodLabel() + " (" + doc.from() + " to " + doc.to() + ")"}, {"Filters", doc.filtersLabel()}}) {
            rows.add(row(r, List.of(str(1, r, kv[0], 1), str(2, r, kv[1], 0))));
            r++;
        }
        for (Object[] block : new Object[][]{{"Data coverage", doc.coverage()}, {"Methodology", doc.methodology()}, {"Notes", doc.notes()}}) {
            @SuppressWarnings("unchecked") List<String> lines = (List<String>) block[1];
            if (lines.isEmpty()) continue;
            r++;
            rows.add(row(r, List.of(str(1, r, (String) block[0], 1))));
            r++;
            for (String l : lines) {
                rows.add(row(r, List.of(str(1, r, l, 0))));
                r++;
            }
        }
        r++;
        rows.add(row(r, List.of(str(1, r, "Sheets", 1))));
        r++;
        for (Section s : doc.sections()) {
            rows.add(row(r, List.of(str(1, r, s.title(), 0), str(2, r, s.rows().isEmpty() ? "no data" : s.rows().size() + " rows", 0))));
            r++;
        }
        return sheet(rows, new int[]{24, 110}, false);
    }

    private static String sectionSheet(Section sec, ZoneId zone) {
        List<String> rows = new ArrayList<>();
        List<String> head = new ArrayList<>();
        for (int c = 0; c < sec.columns().size(); c++) head.add(str(c + 1, 1, header(sec.columns().get(c)), 1));
        rows.add(row(1, head));
        int r = 2;
        if (sec.rows().isEmpty()) {
            rows.add(row(r, List.of(str(1, r, sec.emptyMessage() == null ? "No data for this period." : sec.emptyMessage(), 0))));
        }
        for (List<Object> data : sec.rows()) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < data.size(); c++) {
                Object v = data.get(c);
                if (v == null) continue;
                Unit u = sec.columns().get(c).unit();
                if (v instanceof Number n) {
                    int style = switch (u) {
                        case USD -> 3;
                        case PERCENT, NUMBER -> 4;
                        default -> 2;
                    };
                    cells.add("<c r=\"" + ref(c + 1, r) + "\" s=\"" + style + "\"><v>" + plain(n) + "</v></c>");
                } else {
                    cells.add(str(c + 1, r, text(v, zone), 0));
                }
            }
            rows.add(row(r, cells));
            r++;
        }
        int[] widths = new int[sec.columns().size()];
        for (int c = 0; c < widths.length; c++) {
            int w = header(sec.columns().get(c)).length();
            for (List<Object> data : sec.rows()) {
                Object v = data.get(c);
                if (v != null) w = Math.max(w, v instanceof Number n ? plain(n).length() + 2 : text(v, zone).length());
            }
            widths[c] = Math.min(60, Math.max(8, w + 2));
        }
        return sheet(rows, widths, true);
    }

    private static String sheet(List<String> rows, int[] widths, boolean freezeHeader) {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (freezeHeader) {
            s.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
                    + "</sheetView></sheetViews>");
        }
        s.append("<cols>");
        for (int i = 0; i < widths.length; i++) {
            s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(widths[i])
                    .append("\" customWidth=\"1\"/>");
        }
        s.append("</cols><sheetData>");
        rows.forEach(s::append);
        return s.append("</sheetData></worksheet>").toString();
    }

    private static String row(int r, List<String> cells) {
        return "<row r=\"" + r + "\">" + String.join("", cells) + "</row>";
    }

    private static String str(int col, int row, String v, int style) {
        return "<c r=\"" + ref(col, row) + "\" t=\"inlineStr\"" + (style == 0 ? "" : " s=\"" + style + "\"") + "><is><t xml:space=\"preserve\">"
                + xml(v) + "</t></is></c>";
    }

    static String ref(int col, int row) {
        StringBuilder s = new StringBuilder();
        for (int c = col; c > 0; c = (c - 1) / 26) s.insert(0, (char) ('A' + (c - 1) % 26));
        return s.append(row).toString();
    }

    private static String sheetName(String title, Set<String> used) {
        String base = title.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (base.length() > 28) base = base.substring(0, 28).trim();
        String name = base;
        for (int i = 2; !used.add(name.toLowerCase(Locale.ROOT)); i++) name = base + " " + i;
        return name;
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ---- PDF -----------------------------------------------------------------------------------

    static byte[] pdf(ReportDocument doc, ZoneId zone) {
        String xhtml = html(doc, zone);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the PDF: " + e.getMessage(), e);
        }
    }

    static String html(ReportDocument doc, ZoneId zone) {
        boolean wide = doc.sections().stream().anyMatch(s -> s.columns().size() > 7);
        StringBuilder h = new StringBuilder("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
                + "<title>" + pdfText(doc.title()) + "</title><style>"
                + "@page { size: A4 " + (wide ? "landscape" : "portrait") + "; margin: 16mm 14mm 18mm 14mm;"
                + " @bottom-left { content: 'EstateX - " + pdfText(doc.title()).replace("'", "") + "'; font-size: 8pt; color: #6b7280; }"
                + " @bottom-right { content: 'Page ' counter(page) ' of ' counter(pages); font-size: 8pt; color: #6b7280; } }"
                + "body { font-family: Helvetica, Arial, sans-serif; font-size: 9pt; color: #111827; }"
                + "h1 { font-size: 17pt; margin: 0 0 2pt 0; } .sub { color: #4b5563; margin: 0 0 8pt 0; }"
                + ".meta { border: 1px solid #e5e7eb; background: #f9fafb; padding: 6pt 8pt; margin-bottom: 12pt; }"
                + ".meta td { padding: 1pt 8pt 1pt 0; border: none; } .meta td.k { color: #6b7280; width: 70pt; }"
                + "h2 { font-size: 11pt; margin: 14pt 0 2pt 0; } p.s { color: #4b5563; margin: 0 0 4pt 0; }"
                + "table.d { width: 100%; border-collapse: collapse; -fs-table-paginate: paginate; }"
                + "table.d thead { display: table-header-group; } table.d tr { page-break-inside: avoid; }"
                + "table.d th { text-align: left; font-weight: bold; border-bottom: 1.2px solid #374151; padding: 3pt 4pt; font-size: 8pt; }"
                + "table.d td { border-bottom: 0.5px solid #e5e7eb; padding: 2.5pt 4pt; vertical-align: top; }"
                + "table.d td.n, table.d th.n { text-align: right; } .empty { color: #6b7280; font-style: italic; }"
                + "h3 { font-size: 10pt; margin: 14pt 0 3pt 0; } ul { margin: 0; padding-left: 12pt; } li { margin: 0 0 2pt 0; color: #374151; }"
                + "</style></head><body>");
        h.append("<h1>").append(pdfText(doc.title())).append("</h1>");
        if (doc.subtitle() != null) h.append("<p class=\"sub\">").append(pdfText(doc.subtitle())).append("</p>");
        h.append("<table class=\"meta\"><tr><td class=\"k\">Period</td><td>").append(pdfText(doc.periodLabel())).append(" (")
                .append(doc.from()).append(" to ").append(doc.to()).append(")</td></tr>")
                .append("<tr><td class=\"k\">Filters</td><td>").append(pdfText(doc.filtersLabel())).append("</td></tr>")
                .append("<tr><td class=\"k\">Generated</td><td>").append(pdfText(stamp(doc.generatedAt(), zone) + " by " + doc.generatedBy()))
                .append("</td></tr></table>");
        for (Section sec : doc.sections()) {
            h.append("<h2>").append(pdfText(sec.title())).append("</h2>");
            if (sec.subtitle() != null) h.append("<p class=\"s\">").append(pdfText(sec.subtitle())).append("</p>");
            if (sec.rows().isEmpty()) {
                h.append("<p class=\"empty\">").append(pdfText(sec.emptyMessage() == null ? "No data for this period." : sec.emptyMessage()))
                        .append("</p>");
                continue;
            }
            h.append("<table class=\"d\"><thead><tr>");
            for (Column c : sec.columns()) {
                h.append("<th").append(c.unit() == Unit.TEXT || c.unit() == Unit.DATE ? "" : " class=\"n\"").append(">")
                        .append(pdfText(header(c))).append("</th>");
            }
            h.append("</tr></thead><tbody>");
            for (List<Object> r : sec.rows()) {
                h.append("<tr>");
                for (int i = 0; i < r.size(); i++) {
                    Column c = sec.columns().get(i);
                    boolean numeric = r.get(i) instanceof Number;
                    h.append("<td").append(numeric ? " class=\"n\"" : "").append(">").append(pdfText(display(r.get(i), c.unit(), zone)))
                            .append("</td>");
                }
                h.append("</tr>");
            }
            h.append("</tbody></table>");
        }
        list(h, "Data coverage", doc.coverage());
        list(h, "Methodology", doc.methodology());
        list(h, "Notes", doc.notes());
        return h.append("</body></html>").toString();
    }

    private static void list(StringBuilder h, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        h.append("<h3>").append(title).append("</h3><ul>");
        for (String l : lines) h.append("<li>").append(pdfText(l)).append("</li>");
        h.append("</ul>");
    }

    /** A cell as people read it: thousands separators, $ and % where the column says so, a dash for no data. */
    static String display(Object v, Unit unit, ZoneId zone) {
        if (v == null) return "-";
        if (!(v instanceof Number n)) return text(v, zone);
        BigDecimal d = new BigDecimal(n.toString());
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        return switch (unit) {
            case USD -> {
                f.setMinimumFractionDigits(d.abs().compareTo(BigDecimal.valueOf(1000)) >= 0 ? 0 : 2);
                f.setMaximumFractionDigits(d.abs().compareTo(BigDecimal.valueOf(1000)) >= 0 ? 0 : 2);
                yield (d.signum() < 0 ? "-$" : "$") + f.format(d.abs());
            }
            case PERCENT -> {
                f.setMaximumFractionDigits(1);
                yield f.format(d) + "%";
            }
            case NUMBER -> {
                f.setMaximumFractionDigits(2);
                yield f.format(d);
            }
            default -> {
                f.setMaximumFractionDigits(0);
                yield f.format(d.setScale(0, RoundingMode.HALF_UP));
            }
        };
    }

    // ---- Shared ---------------------------------------------------------------------------------

    private static String header(Column c) {
        return switch (c.unit()) {
            case USD -> c.label() + " (USD)";
            case PERCENT -> c.label() + " (%)";
            default -> c.label();
        };
    }

    private static String text(Object v, ZoneId zone) {
        if (v instanceof Instant i) return stamp(i, zone);
        if (v instanceof LocalDate d) return d.toString();
        return v.toString();
    }

    private static String plain(Number n) {
        return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    }

    private static String stamp(Instant i, ZoneId zone) {
        return STAMP.format(i.atZone(zone)) + " (" + zone.getId() + ")";
    }

    /** XML-escapes and drops characters XML can't hold. */
    static String xml(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** The PDF uses the built-in Helvetica, which lacks a few symbols our text uses; swap them for plain ones. */
    static String pdfText(String s) {
        if (s == null) return "";
        String t = s.replace('−', '-').replace("×", "x").replace("÷", "/").replace("≤", "<=").replace("≥", ">=")
                .replace("→", "->").replace("≈", "~").replace("√", "sqrt");
        return xml(t);
    }
}
