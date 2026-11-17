package com.example.primenestprop.report;

import com.example.primenestprop.organisation.OrgTheme;
import com.example.primenestprop.organisation.Organisation;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.TitleType;
import com.example.primenestprop.showing.Showing;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** XHTML for the seller report PDF, in the organisation's brand colours. Every value is escaped. */
final class SellerReportHtml {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK);

    private SellerReportHtml() {
    }

    static String render(SellerReportService.Data d) {
        Organisation org = d.org();
        OrgTheme t = org.getTheme();
        String navy = color(t == null ? null : t.getLightPrimary(), "#0B1D32");
        String ivory = color(t == null ? null : t.getLightSurface(), "#F5F2EA");
        String muted = color(t == null ? null : t.getLightMuted(), "#5E6873");
        String silver = color(t == null ? null : t.getLightAccent(), "#C9CED4");
        Property p = d.property();
        String currency = p.getCurrency();

        StringBuilder h = new StringBuilder(8_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style>")
                .append("@page { size: A4; margin: 0; }")
                .append("body { margin:0; font-family: Helvetica, Arial, sans-serif; color:").append(navy).append("; font-size: 10.5pt; }")
                .append(".band { background:").append(navy).append("; color:").append(ivory).append("; padding: 22pt 30pt 18pt; }")
                .append(".brand { font-size: 18pt; font-weight: bold; letter-spacing: 3pt; }")
                .append(".slogan { color:").append(silver).append("; font-size: 9pt; margin-top: 2pt; }")
                .append(".title { font-size: 15pt; font-weight: bold; margin-top: 14pt; }")
                .append(".sub { color:").append(silver).append("; font-size: 9.5pt; }")
                .append(".page { padding: 18pt 30pt; background:").append(ivory).append("; }")
                .append("h2 { font-size: 11.5pt; margin: 16pt 0 6pt; border-bottom: 1pt solid ").append(silver).append("; padding-bottom: 3pt; }")
                .append(".kpis { width:100%; border-collapse: separate; border-spacing: 6pt 0; margin: 0 -6pt; }")
                .append(".kpi { background:#FFFFFF; border:1pt solid ").append(silver).append("; padding: 8pt; text-align:center; }")
                .append(".kpi b { display:block; font-size: 17pt; }")
                .append(".kpi span { color:").append(muted).append("; font-size: 8.5pt; }")
                .append("table.list { width:100%; border-collapse: collapse; }")
                .append("table.list td, table.list th { text-align:left; padding: 4pt 4pt; border-bottom: 0.5pt solid ").append(silver).append("; font-size: 9.5pt; }")
                .append("table.list th { color:").append(muted).append("; font-weight: normal; }")
                .append(".advice { background:").append(navy).append("; color:").append(ivory).append("; padding: 10pt 12pt; font-size: 10.5pt; }")
                .append(".muted { color:").append(muted).append("; }")
                .append(".badge { border:1pt solid #B45309; color:#92400E; padding: 1pt 5pt; font-size: 8.5pt; font-weight:bold; }")
                .append(".foot { color:").append(muted).append("; font-size: 8pt; padding: 10pt 30pt 16pt; background:").append(ivory).append("; }")
                .append("</style></head><body>");

        h.append("<div class=\"band\"><div class=\"brand\">").append(esc(org.getName().toUpperCase(Locale.ROOT))).append("</div>");
        if (org.getSlogan() != null) h.append("<div class=\"slogan\">").append(esc(org.getSlogan())).append("</div>");
        h.append("<div class=\"title\">Weekly seller report</div><div class=\"sub\">")
                .append(esc(p.getTitle())).append(" · ").append(esc(p.getSuburb())).append(", ").append(esc(p.getCity()))
                .append("<br/>").append(SellerReportService.day(d.from())).append(" to ").append(SellerReportService.day(d.to()))
                .append("</div></div>");

        h.append("<div class=\"page\">");
        h.append("<p>Asking ").append(PriceAdvice.money(d.askingCents(), currency))
                .append(" · ").append(d.daysOnMarket()).append(" days on the market");
        if (p.getTitleType() == TitleType.CESSION) h.append(" · <span class=\"badge\">CESSION</span>");
        h.append("</p>");

        h.append("<table class=\"kpis\"><tr>")
                .append(kpi(d.enquiriesWeek(), "enquiries this week"))
                .append(kpi(d.enquiriesTotal(), "enquiries since launch"))
                .append(kpi(d.viewingsWeek().size(), "viewings this week"))
                .append(kpi(d.openOffers().size(), "offers on the table"))
                .append("</tr></table>");

        h.append("<h2>Our advice</h2><div class=\"advice\">").append(esc(d.advice().text())).append("</div>");
        if (d.pricing() != null) {
            h.append("<p class=\"muted\">Our last price recommendation: ")
                    .append(PriceAdvice.money(d.pricing().getRecommendedCents(), currency))
                    .append(" (from ").append(d.pricing().getComparableCount()).append(" comparable asking prices).</p>");
        }

        h.append("<h2>Where enquiries came from this week</h2>");
        if (d.enquiriesByChannelWeek().isEmpty()) {
            h.append("<p class=\"muted\">No enquiries this week.</p>");
        } else {
            h.append("<table class=\"list\"><tr><th>Channel</th><th>Enquiries</th></tr>");
            for (Map.Entry<String, Long> e : d.enquiriesByChannelWeek().entrySet()) {
                h.append("<tr><td>").append(esc(e.getKey())).append("</td><td>").append(e.getValue()).append("</td></tr>");
            }
            h.append("</table>");
        }

        h.append("<h2>Viewings and feedback</h2>");
        if (d.viewingsWeek().isEmpty()) {
            h.append("<p class=\"muted\">No viewings this week.</p>");
        } else {
            h.append("<table class=\"list\"><tr><th>When</th><th>Outcome</th><th>Interest</th><th>Price</th><th>Feedback</th></tr>");
            for (Showing s : d.viewingsWeek()) {
                h.append("<tr><td>").append(esc(s.getScheduledAt().atZone(java.time.ZoneId.of(org.getTimezone())).format(WHEN)))
                        .append("</td><td>").append(esc(nice(s.getStatus().name())))
                        .append("</td><td>").append(s.getInterest() == null ? "" : esc(nice(s.getInterest().name())))
                        .append("</td><td>").append(s.getPriceOpinion() == null ? "" : esc(nice(s.getPriceOpinion().name())))
                        .append("</td><td>").append(s.getFeedback() == null ? "" : esc(s.getFeedback()))
                        .append("</td></tr>");
            }
            h.append("</table>");
        }
        h.append("<p class=\"muted\">All viewings so far: ").append(d.completedTotal()).append(" completed · ")
                .append(d.hot()).append(" hot, ").append(d.warm()).append(" warm, ").append(d.cold()).append(" cold · ")
                .append(d.tooHighTotal()).append(" felt the price was too high.</p>");

        h.append("<h2>Offers</h2>");
        if (d.bestOffer() == null) {
            h.append("<p class=\"muted\">No offers yet.</p>");
        } else {
            h.append("<p>Best offer: <b>").append(PriceAdvice.money(d.bestOffer().getAmountCents(), d.bestOffer().getCurrency()))
                    .append("</b> (").append(esc(nice(d.bestOffer().getStatus().name()))).append("). ")
                    .append(d.openOffers().size()).append(" offer(s) in progress.</p>");
        }

        if (d.campaign() != null) {
            h.append("<h2>Marketing campaign</h2><p>Day ").append(d.campaign().today()).append(" of 21 · ")
                    .append(d.campaign().tasksDone()).append(" of ").append(d.campaign().tasksTotal())
                    .append(" planned activities done.</p>");
        }
        h.append("</div>");

        h.append("<div class=\"foot\">").append(esc(org.getName()));
        if (org.getPhone() != null) h.append(" · ").append(esc(org.getPhone()));
        if (org.getWhatsappNumber() != null) h.append(" · WhatsApp wa.me/").append(esc(org.getWhatsappNumber()));
        if (org.getInstagramHandle() != null) h.append(" · Instagram @").append(esc(org.getInstagramHandle()));
        h.append("<br/>");
        if (org.getPreaName() != null) h.append("Principal Registered Estate Agent: ").append(esc(org.getPreaName())).append(". ");
        if (org.getEacRegistrationNumber() != null) h.append("EAC registration ").append(esc(org.getEacRegistrationNumber())).append(". ");
        h.append("Figures are from enquiries, viewings and offers recorded by the agency. Comparable prices are asking prices, not achieved sales.</div>");
        h.append("</body></html>");
        return h.toString();
    }

    private static String kpi(long value, String label) {
        return "<td class=\"kpi\"><b>" + value + "</b><span>" + esc(label) + "</span></td>";
    }

    private static String nice(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String color(String value, String fallback) {
        return value != null && value.matches("^#[0-9A-Fa-f]{6}$") ? value : fallback;
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t') continue; // control chars break XHTML
                    b.append(c);
                }
            }
        }
        return b.toString();
    }
}
