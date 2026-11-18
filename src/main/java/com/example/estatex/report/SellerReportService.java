package com.example.estatex.report;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.campaign.CampaignDtos;
import com.example.estatex.campaign.CampaignService;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.common.DocumentStore;
import com.example.estatex.enquiry.Enquiry;
import com.example.estatex.enquiry.EnquiryRepository;
import com.example.estatex.intake.IntakeService;
import com.example.estatex.intake.PricingRecommendation;
import com.example.estatex.listing.ListingCompliance;
import com.example.estatex.listing.ListingComplianceRepository;
import com.example.estatex.offer.Offer;
import com.example.estatex.offer.OfferRepository;
import com.example.estatex.offer.OfferStatus;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.organisation.Organisation;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.showing.PriceOpinion;
import com.example.estatex.showing.Showing;
import com.example.estatex.showing.ShowingRepository;
import com.example.estatex.showing.ShowingStatus;
import com.example.estatex.user.AppUser;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly seller report: what happened on the owner's listing this week (enquiries by channel,
 * viewings and what viewers said, offers, campaign progress) plus price advice, as a branded PDF
 * the negotiator shares by WhatsApp or email through an expiring link.
 */
@Service
public class SellerReportService {
    static final Duration LINK_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final OrgAccessService access;
    private final PropertyRepository properties;
    private final ListingComplianceRepository compliances;
    private final EnquiryRepository enquiries;
    private final ShowingRepository showings;
    private final OfferRepository offers;
    private final CampaignService campaigns;
    private final IntakeService intake;
    private final SellerReportRepository reports;
    private final DocumentStore store;
    private final AuditService audit;
    private final BusinessTime time;

    public SellerReportService(OrgAccessService access, PropertyRepository properties, ListingComplianceRepository compliances,
                               EnquiryRepository enquiries, ShowingRepository showings, OfferRepository offers,
                               CampaignService campaigns, IntakeService intake, SellerReportRepository reports,
                               DocumentStore store, AuditService audit, BusinessTime time) {
        this.access = access;
        this.properties = properties;
        this.compliances = compliances;
        this.enquiries = enquiries;
        this.showings = showings;
        this.offers = offers;
        this.campaigns = campaigns;
        this.intake = intake;
        this.reports = reports;
        this.store = store;
        this.audit = audit;
        this.time = time;
    }

    public record ReportSummary(Long id, LocalDate weekEnding, String shareToken, String adviceKind, String adviceText,
                                Instant generatedAt, Instant expiresAt) {
        static ReportSummary from(SellerReport r) {
            return new ReportSummary(r.getId(), r.getWeekEnding(), r.getShareToken(), r.getAdviceKind(), r.getAdviceText(),
                    r.getGeneratedAt(), r.getExpiresAt());
        }
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> list(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        listingIn(ctx, propertyId);
        return reports.findByPropertyIdOrderByGeneratedAtDesc(propertyId).stream().map(ReportSummary::from).toList();
    }

    @Transactional
    public ReportSummary generate(String slug, Long propertyId, LocalDate weekEnding, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        Property p = listingIn(ctx, propertyId);
        LocalDate end = weekEnding == null ? time.today() : weekEnding;
        if (end.isAfter(time.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The report week can't end in the future.");
        }
        Data d = gather(ctx.organisation(), p, end);
        byte[] pdf = render(SellerReportHtml.render(d));

        SellerReport r = new SellerReport();
        r.setOrganisationId(ctx.organisation().getId());
        r.setPropertyId(p.getId());
        r.setWeekEnding(end);
        r.setShareToken(newToken());
        r.setStorageKey("reports/" + ctx.organisation().getId() + "/" + p.getId() + "/seller-report-" + end + "-" + r.getShareToken().substring(0, 8) + ".pdf");
        r.setAdviceKind(d.advice().kind().name());
        r.setAdviceText(d.advice().text());
        r.setGeneratedByUserId(user.getId());
        r.setExpiresAt(Instant.now().plus(LINK_LIFETIME));
        store.putBytes(r.getStorageKey(), pdf, "application/pdf");
        reports.save(r);
        audit.record(user, entry("SELLER_REPORT_GENERATED", "SellerReport", r.getId()).organisation(r.getOrganisationId())
                .metadata(Map.of("propertyId", p.getId(), "weekEnding", end.toString(), "advice", d.advice().kind().name())));
        return ReportSummary.from(r);
    }

    /** Public, by share token. Expired or unknown tokens look the same (404). */
    @Transactional(readOnly = true)
    public DocumentStore.Located shared(String token) {
        SellerReport r = reports.findByShareToken(token)
                .filter(x -> x.getExpiresAt() == null || x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This report link has expired or doesn't exist."));
        return store.locate(r.getStorageKey());
    }

    // -- data -------------------------------------------------------------------------------

    record Data(Organisation org, Property property, LocalDate from, LocalDate to, long daysOnMarket,
                Map<String, Long> enquiriesByChannelWeek, long enquiriesWeek, long enquiriesTotal,
                List<Showing> viewingsWeek, int completedTotal, int tooHighTotal, long hot, long warm, long cold,
                List<Offer> openOffers, Offer bestOffer, CampaignDtos.CampaignResponse campaign,
                PricingRecommendation pricing, PriceAdvice.Advice advice, long askingCents) {
    }

    Data gather(Organisation org, Property p, LocalDate end) {
        LocalDate start = end.minusDays(6);
        Instant from = start.atStartOfDay(time.zone()).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(time.zone()).toInstant();
        ListingCompliance c = compliances.findByPropertyId(p.getId()).orElse(null);
        Instant listedAt = c != null && c.getPublishedAt() != null ? c.getPublishedAt() : p.getCreatedAt();
        long daysOnMarket = Math.max(0, ChronoUnit.DAYS.between(listedAt.atZone(time.zone()).toLocalDate(), end));

        List<Enquiry> all = enquiries.findByOrganisationIdAndPropertyIdOrderByCapturedAtDesc(org.getId(), p.getId());
        Map<String, Long> byChannel = new TreeMap<>();
        long week = 0;
        for (Enquiry e : all) {
            if (!e.getCapturedAt().isBefore(from) && e.getCapturedAt().isBefore(to)) {
                byChannel.merge(label(e.getChannel().name()), 1L, Long::sum);
                week++;
            }
        }
        List<Showing> allViewings = showings.findByPropertyIdOrderByScheduledAtDesc(p.getId());
        List<Showing> viewingsWeek = allViewings.stream()
                .filter(s -> !s.getScheduledAt().isBefore(from) && s.getScheduledAt().isBefore(to))
                .filter(s -> s.getStatus() != ShowingStatus.CANCELLED).toList();
        List<Showing> completed = allViewings.stream().filter(s -> s.getStatus() == ShowingStatus.COMPLETED).toList();
        int tooHigh = (int) completed.stream().filter(s -> s.getPriceOpinion() == PriceOpinion.TOO_HIGH).count();
        long hot = completed.stream().filter(s -> s.getInterest() != null && s.getInterest().name().equals("HOT")).count();
        long warm = completed.stream().filter(s -> s.getInterest() != null && s.getInterest().name().equals("WARM")).count();
        long cold = completed.stream().filter(s -> s.getInterest() != null && s.getInterest().name().equals("COLD")).count();

        List<Offer> propertyOffers = offers.findByPropertyIdOrderByCreatedAtDesc(p.getId());
        List<Offer> open = propertyOffers.stream().filter(o -> o.getStatus().isOpen() || o.getStatus() == OfferStatus.ACCEPTED).toList();
        Offer best = open.stream().max((a, b) -> Long.compare(a.getAmountCents(), b.getAmountCents())).orElse(null);

        long asking = p.getPrice() == null ? 0 : p.getPrice().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        PricingRecommendation pricing = intake.latestRecommendation(p.getId());
        PriceAdvice.Advice advice = PriceAdvice.advise(completed.size(), tooHigh, (int) week, daysOnMarket, asking,
                pricing == null ? null : pricing.getRecommendedCents(), p.getCurrency());
        return new Data(org, p, start, end, daysOnMarket, byChannel, week, all.size(), viewingsWeek, completed.size(),
                tooHigh, hot, warm, cold, open, best, campaigns.summaryFor(p), pricing, advice, asking);
    }

    static byte[] render(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the PDF");
        }
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String label(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return switch (enumName) {
            case "WHATSAPP" -> "WhatsApp";
            case "WALK_IN" -> "Walk-in";
            default -> Character.toUpperCase(s.charAt(0)) + s.substring(1);
        };
    }

    static String day(LocalDate d) {
        return d.format(DAY);
    }

    static long cents(BigDecimal amount) {
        return amount == null ? 0 : amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private Property listingIn(OrgContext ctx, Long propertyId) {
        return properties.findById(propertyId)
                .filter(p -> ctx.organisation().getId().equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    }
}
