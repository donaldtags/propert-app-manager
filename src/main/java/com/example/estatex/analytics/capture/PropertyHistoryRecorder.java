package com.example.estatex.analytics.capture;

import com.example.estatex.property.Property;
import com.example.estatex.user.AppUser;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.TransactionCompletionCallbacks.BeforeCompletionCallback;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Records every change to a listing's price, status, verification, type, location or
 * characteristics in {@code property_events}, whichever service made the change.
 *
 * <p>It hooks into Hibernate itself rather than into each service, because about ten code paths
 * change a listing's status (offers, mandates, billing, reports, the owner's own edits). Hibernate
 * hands the listener the old and new value of every column, so nothing can slip past.
 *
 * <p>The history rows are inserted just before the transaction commits, in the same
 * transaction, so a change and its history are saved together or not at all. Changes made with
 * raw SQL bypass this; none exist today, and new ones must write their own event.
 */
@Component
public class PropertyHistoryRecorder implements PostInsertEventListener, PostUpdateEventListener {
    private static final String INSERT = """
            insert into property_events (property_id, event_type, listing_type, status, verification_status,
                country, city, suburb, category, bedrooms, bathrooms, floor_area_m2, price, currency, latitude,
                longitude, organisation_id, previous_price, previous_status, change_summary, occurred_at,
                recorded_at, actor_user_id, source)
            values (:propertyId, :eventType, :listingType, :status, :verificationStatus, :country, :city, :suburb,
                :category, :bedrooms, :bathrooms, :floorAreaM2, :price, :currency, :latitude, :longitude,
                :organisationId, :previousPrice, :previousStatus, :changeSummary, :occurredAt, :recordedAt,
                :actorUserId, 'APP')""";

    private final EntityManagerFactory emf;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PropertyHistoryRecorder(EntityManagerFactory emf) {
        this(emf, Clock.systemUTC());
    }

    PropertyHistoryRecorder(EntityManagerFactory emf, Clock clock) {
        this.emf = emf;
        this.clock = clock;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = emf.unwrap(SessionFactoryImpl.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!(event.getEntity() instanceof Property p)) return;
        Row row = Row.of(p, PropertyEventType.CREATED);
        schedule(event.getSession(), List.of(row));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!(event.getEntity() instanceof Property p) || event.getOldState() == null) return;
        String[] names = event.getPersister().getPropertyNames();
        Object[] before = event.getOldState();
        Object[] after = event.getState();
        List<Row> rows = diff(p, names, before, after);
        if (!rows.isEmpty()) schedule(event.getSession(), rows);
    }

    /** Which events a change produces. Package-private for tests. */
    static List<Row> diff(Property p, String[] names, Object[] before, Object[] after) {
        List<Row> rows = new ArrayList<>();
        Object oldPrice = value("price", names, before);
        if (!sameAmount(oldPrice, value("price", names, after))) {
            Row r = Row.of(p, PropertyEventType.PRICE_CHANGED);
            r.previousPrice = (BigDecimal) oldPrice;
            rows.add(r);
        }
        Object oldStatus = value("status", names, before);
        if (!Objects.equals(oldStatus, value("status", names, after))) {
            Row r = Row.of(p, PropertyEventType.STATUS_CHANGED);
            r.previousStatus = oldStatus == null ? null : oldStatus.toString();
            rows.add(r);
        }
        changed(p, rows, PropertyEventType.VERIFICATION_CHANGED, names, before, after, "verificationStatus");
        changed(p, rows, PropertyEventType.LISTING_TYPE_CHANGED, names, before, after, "listingType");
        changed(p, rows, PropertyEventType.LOCATION_CHANGED, names, before, after,
                "country", "city", "suburb", "latitude", "longitude");
        changed(p, rows, PropertyEventType.CHARACTERISTICS_CHANGED, names, before, after,
                "bedrooms", "bathrooms", "categoryCode", "floorAreaM2");
        return rows;
    }

    private static void changed(Property p, List<Row> rows, PropertyEventType type, String[] names,
                                Object[] before, Object[] after, String... fields) {
        StringBuilder summary = new StringBuilder();
        for (String f : fields) {
            Object a = value(f, names, before);
            Object b = value(f, names, after);
            boolean same = (a instanceof BigDecimal || b instanceof BigDecimal) ? sameAmount(a, b) : Objects.equals(a, b);
            if (!same) {
                if (!summary.isEmpty()) summary.append("; ");
                summary.append(f).append(' ').append(a).append('→').append(b);
            }
        }
        if (!summary.isEmpty()) {
            Row r = Row.of(p, type);
            r.changeSummary = summary.length() > 500 ? summary.substring(0, 500) : summary.toString();
            rows.add(r);
        }
    }

    private static boolean sameAmount(Object a, Object b) {
        if (a == null || b == null) return a == b;
        return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
    }

    private static Object value(String field, String[] names, Object[] state) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(field)) return state[i];
        }
        return null;
    }

    private void schedule(SharedSessionContractImplementor session, List<Row> rows) {
        Instant now = clock.instant();
        Long actor = currentUserId();
        session.getTransactionCompletionCallbacks().registerCallback((BeforeCompletionCallback) s -> {
            for (Row r : rows) {
                s.createNativeMutationQuery(INSERT)
                        .setParameter("propertyId", r.propertyId)
                        .setParameter("eventType", r.type.name())
                        .setParameter("listingType", r.listingType)
                        .setParameter("status", r.status)
                        .setParameter("verificationStatus", r.verificationStatus)
                        .setParameter("country", r.country)
                        .setParameter("city", r.city)
                        .setParameter("suburb", r.suburb)
                        .setParameter("category", r.category)
                        .setParameter("bedrooms", r.bedrooms)
                        .setParameter("bathrooms", r.bathrooms)
                        .setParameter("floorAreaM2", r.floorAreaM2)
                        .setParameter("price", r.price)
                        .setParameter("currency", r.currency)
                        .setParameter("latitude", r.latitude)
                        .setParameter("longitude", r.longitude)
                        .setParameter("organisationId", r.organisationId)
                        .setParameter("previousPrice", r.previousPrice)
                        .setParameter("previousStatus", r.previousStatus)
                        .setParameter("changeSummary", r.changeSummary)
                        .setParameter("occurredAt", now)
                        .setParameter("recordedAt", now)
                        .setParameter("actorUserId", actor)
                        .executeUpdate();
            }
        });
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUser u ? u.getId() : null;
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    /** One history row, captured at flush time so later changes in the same transaction can't alter it. */
    static final class Row {
        PropertyEventType type;
        Long propertyId;
        String listingType;
        String status;
        String verificationStatus;
        String country;
        String city;
        String suburb;
        String category;
        Integer bedrooms;
        Integer bathrooms;
        Integer floorAreaM2;
        BigDecimal price;
        String currency;
        BigDecimal latitude;
        BigDecimal longitude;
        Long organisationId;
        BigDecimal previousPrice;
        String previousStatus;
        String changeSummary;

        static Row of(Property p, PropertyEventType type) {
            Row r = new Row();
            r.type = type;
            r.propertyId = p.getId();
            r.listingType = p.getListingType() == null ? null : p.getListingType().name();
            r.status = p.getStatus() == null ? null : p.getStatus().name();
            r.verificationStatus = p.getVerificationStatus() == null ? null : p.getVerificationStatus().name();
            r.country = p.getCountry();
            r.city = p.getCity();
            r.suburb = p.getSuburb();
            r.category = p.getCategory() == null ? null : p.getCategory().name();
            r.bedrooms = p.getBedrooms();
            r.bathrooms = p.getBathrooms();
            r.floorAreaM2 = p.getFloorAreaM2();
            r.price = p.getPrice();
            r.currency = p.getCurrency();
            r.latitude = p.getLatitude();
            r.longitude = p.getLongitude();
            r.organisationId = p.getOrganisationId();
            return r;
        }

        PropertyEventType type() {
            return type;
        }

        BigDecimal previousPrice() {
            return previousPrice;
        }

        String previousStatus() {
            return previousStatus;
        }

        String changeSummary() {
            return changeSummary;
        }
    }
}
