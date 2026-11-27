-- Subscription history starts now: one BASELINE row per subscription with its current plan, status and
-- the plan's current price. Earlier MRR and churn can't be reconstructed and are flagged as such.
INSERT INTO subscription_events (subscription_id, user_id, event_type, plan, status, monthly_price, currency, occurred_at,
    recorded_at, source)
SELECT s.id, s.user_id, 'BASELINE', s.plan, s.status, p.monthly_price, p.currency, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'BASELINE'
FROM landlord_subscriptions s LEFT JOIN subscription_plan_settings p ON p.plan = s.plan
WHERE NOT EXISTS (SELECT 1 FROM subscription_events e WHERE e.subscription_id = s.id);
INSERT INTO analytics_capture_starts (source, started_at)
SELECT 'subscription_events', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM analytics_capture_starts WHERE source = 'subscription_events');
