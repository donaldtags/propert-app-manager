package com.example.estatex.campaign;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.enquiry.Enquiry;
import com.example.estatex.enquiry.EnquiryRepository;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.showing.ShowingRepository;
import com.example.estatex.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    static final int LENGTH_DAYS = 21;

    private final OrgAccessService access;
    private final MarketingCampaignRepository campaigns;
    private final CampaignTaskRepository tasks;
    private final PropertyRepository properties;
    private final EnquiryRepository enquiries;
    private final ShowingRepository showings;
    private final AuditService audit;
    private final BusinessTime time;

    public CampaignService(OrgAccessService access, MarketingCampaignRepository campaigns, CampaignTaskRepository tasks,
                           PropertyRepository properties, EnquiryRepository enquiries, ShowingRepository showings,
                           AuditService audit, BusinessTime time) {
        this.access = access;
        this.campaigns = campaigns;
        this.tasks = tasks;
        this.properties = properties;
        this.enquiries = enquiries;
        this.showings = showings;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public CampaignDtos.CampaignResponse current(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        Property p = listingIn(ctx, propertyId);
        return campaigns.findFirstByPropertyIdAndStoppedFalseOrderByStartDateDesc(p.getId())
                .map(c -> response(c, p))
                .orElse(null);
    }

    /** Starts the 21-day plan with today as day 1. Only for a live listing; one running campaign at a time. */
    @Transactional
    public CampaignDtos.CampaignResponse start(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        Property p = listingIn(ctx, propertyId);
        if (p.getStatus() != PropertyStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "Publish the listing before starting its marketing campaign.");
        }
        MarketingCampaign existing = campaigns.findFirstByPropertyIdAndStoppedFalseOrderByStartDateDesc(p.getId()).orElse(null);
        if (existing != null) {
            if (!time.today().isAfter(existing.getStartDate().plusDays(LENGTH_DAYS - 1))) {
                return response(existing, p); // already running: idempotent
            }
            existing.setStopped(true); // previous one has finished; start a fresh one
        }
        MarketingCampaign c = new MarketingCampaign();
        c.setOrganisationId(ctx.organisation().getId());
        c.setPropertyId(p.getId());
        c.setStartDate(time.today());
        c.setCreatedByUserId(user.getId());
        campaigns.save(c);
        for (CampaignTemplate.Task t : CampaignTemplate.TASKS) {
            CampaignTask task = new CampaignTask();
            task.setCampaignId(c.getId());
            task.setDay(t.day());
            task.setChannel(t.channel());
            task.setTitle(t.title());
            tasks.save(task);
        }
        audit.record(user, entry("CAMPAIGN_STARTED", "MarketingCampaign", c.getId()).organisation(c.getOrganisationId())
                .metadata(Map.of("propertyId", p.getId(), "startDate", c.getStartDate().toString())));
        return response(c, p);
    }

    /** Sets a task done/not done. Setting state (not toggling) makes offline replays harmless. */
    @Transactional
    public CampaignDtos.CampaignResponse setTask(String slug, Long taskId, CampaignDtos.SetTaskRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        CampaignTask task = tasks.findById(taskId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        MarketingCampaign c = campaigns.findById(task.getCampaignId())
                .filter(x -> x.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (r.done() && task.getDoneAt() == null) {
            task.setDoneAt(Instant.now());
            task.setDoneByUserId(user.getId());
        } else if (!r.done()) {
            task.setDoneAt(null);
            task.setDoneByUserId(null);
        }
        if (r.note() != null) {
            task.setNote(r.note().isBlank() ? null : r.note().trim());
        }
        return response(c, properties.findById(c.getPropertyId()).orElseThrow());
    }

    /** Internal (no auth check): the running campaign for a listing, for reports. */
    @Transactional(readOnly = true)
    public CampaignDtos.CampaignResponse summaryFor(Property p) {
        return campaigns.findFirstByPropertyIdAndStoppedFalseOrderByStartDateDesc(p.getId()).map(c -> response(c, p)).orElse(null);
    }

    CampaignDtos.CampaignResponse response(MarketingCampaign c, Property p) {
        LocalDate today = time.today();
        int day = (int) Math.min(LENGTH_DAYS, Math.max(1, ChronoUnit.DAYS.between(c.getStartDate(), today) + 1));
        List<CampaignDtos.TaskResponse> items = tasks.findByCampaignIdOrderByDayAscIdAsc(c.getId()).stream()
                .map(t -> {
                    LocalDate due = c.getStartDate().plusDays(t.getDay() - 1L);
                    return new CampaignDtos.TaskResponse(t.getId(), t.getDay(), due, t.getChannel(), t.getTitle(),
                            t.getDoneAt() != null, t.getDoneAt(), t.getDoneAt() == null && due.isBefore(today), t.getNote());
                }).toList();
        Instant from = c.getStartDate().atStartOfDay(time.zone()).toInstant();
        Map<String, Long> byChannel = new TreeMap<>();
        long total = 0;
        for (Enquiry e : enquiries.findByOrganisationIdAndPropertyIdOrderByCapturedAtDesc(c.getOrganisationId(), p.getId())) {
            if (!e.getCapturedAt().isBefore(from)) {
                byChannel.merge(e.getChannel().name(), 1L, Long::sum);
                total++;
            }
        }
        long viewings = showings.findByPropertyIdOrderByScheduledAtDesc(p.getId()).stream()
                .filter(s -> !s.getScheduledAt().isBefore(from)).count();
        int done = (int) items.stream().filter(CampaignDtos.TaskResponse::done).count();
        return new CampaignDtos.CampaignResponse(c.getId(), p.getId(), c.getStartDate(),
                c.getStartDate().plusDays(LENGTH_DAYS - 1L), day, c.isStopped(), done, items.size(), items, byChannel,
                total, viewings);
    }

    private Property listingIn(OrgContext ctx, Long propertyId) {
        return properties.findById(propertyId)
                .filter(p -> ctx.organisation().getId().equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    }
}
