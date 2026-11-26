-- When each analytics capture table started recording. Earlier periods can't be measured from
-- it and are flagged, never reported as zero. Listing history began with V6.
INSERT INTO analytics_capture_starts (source, started_at)
SELECT 'property_events', COALESCE((SELECT MIN(occurred_at) FROM property_events), CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM analytics_capture_starts WHERE source = 'property_events');
INSERT INTO analytics_capture_starts (source, started_at)
SELECT 'marketplace_events', COALESCE((SELECT MIN(occurred_at) FROM marketplace_events), CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM analytics_capture_starts WHERE source = 'marketplace_events');
INSERT INTO analytics_capture_starts (source, started_at)
SELECT 'user_activity_days', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM analytics_capture_starts WHERE source = 'user_activity_days');
