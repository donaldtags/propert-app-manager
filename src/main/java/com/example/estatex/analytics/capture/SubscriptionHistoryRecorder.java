package com.example.estatex.analytics.capture;

import com.example.estatex.subscription.LandlordSubscription;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
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
import org.springframework.stereotype.Component;

/**
 * Records every change of a subscription's plan or status in {@code subscription_events}, in the
 * same transaction as the change, whichever code path made it (checkout, renewal sweep,
 * downgrade, staff). Same mechanism as {@link PropertyHistoryRecorder}. The plan's price is read
 * at that moment, so later price changes don't rewrite history.
 */
@Component
public class SubscriptionHistoryRecorder implements PostInsertEventListener, PostUpdateEventListener {
    private static final String INSERT = """
            insert into subscription_events (subscription_id, user_id, event_type, plan, status, monthly_price, currency,
                previous_plan, previous_status, occurred_at, recorded_at, source)
            values (:sub, :user, :type, :plan, :status,
                (select monthly_price from subscription_plan_settings where plan = :plan),
                (select currency from subscription_plan_settings where plan = :plan),
                :prevPlan, :prevStatus, :at, :at, 'APP')""";

    private final EntityManagerFactory emf;

    public SubscriptionHistoryRecorder(EntityManagerFactory emf) {
        this.emf = emf;
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
        if (event.getEntity() instanceof LandlordSubscription s) schedule(event.getSession(), s, "CREATED", null, null);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!(event.getEntity() instanceof LandlordSubscription s) || event.getOldState() == null) return;
        String[] names = event.getPersister().getPropertyNames();
        Object oldPlan = value("plan", names, event.getOldState());
        Object oldStatus = value("status", names, event.getOldState());
        if (Objects.equals(oldPlan, s.getPlan()) && Objects.equals(oldStatus, s.getStatus())) return; // only period dates moved
        schedule(event.getSession(), s, "CHANGED", oldPlan == null ? null : oldPlan.toString(),
                oldStatus == null ? null : oldStatus.toString());
    }

    private static Object value(String field, String[] names, Object[] state) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(field)) return state[i];
        return null;
    }

    private void schedule(SharedSessionContractImplementor session, LandlordSubscription s, String type, String prevPlan,
                          String prevStatus) {
        Long sub = s.getId();
        Long user = s.getUser().getId();
        String plan = s.getPlan().name();
        String status = s.getStatus().name();
        Instant at = Instant.now();
        session.getTransactionCompletionCallbacks().registerCallback((BeforeCompletionCallback) ses ->
                ses.createNativeMutationQuery(INSERT)
                        .setParameter("sub", sub).setParameter("user", user).setParameter("type", type)
                        .setParameter("plan", plan).setParameter("status", status)
                        .setParameter("prevPlan", prevPlan).setParameter("prevStatus", prevStatus)
                        .setParameter("at", at)
                        .executeUpdate());
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
