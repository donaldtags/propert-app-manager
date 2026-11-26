-- Listing history starts now. Nothing earlier was recorded, so each listing gets one BASELINE
-- row holding its current state; analytics never shows a trend from before this row.
-- Floor area: copy it from the listing inspection where an agency recorded one.
UPDATE properties SET floor_area_m2 = (
    SELECT li.floor_area_m2 FROM listing_inspections li WHERE li.property_id = properties.id)
WHERE floor_area_m2 IS NULL
  AND EXISTS (SELECT 1 FROM listing_inspections li WHERE li.property_id = properties.id AND li.floor_area_m2 > 0);

INSERT INTO property_events (property_id, event_type, listing_type, status, verification_status, country, city,
    suburb, category, bedrooms, bathrooms, floor_area_m2, price, currency, latitude, longitude, organisation_id,
    occurred_at, recorded_at, source)
SELECT p.id, 'BASELINE', p.listing_type, p.status, p.verification_status, p.country, p.city, p.suburb,
    p.category, p.bedrooms, p.bathrooms, p.floor_area_m2, p.price, p.currency, p.latitude, p.longitude,
    p.organisation_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'BASELINE'
FROM properties p
WHERE NOT EXISTS (SELECT 1 FROM property_events e WHERE e.property_id = p.id);
