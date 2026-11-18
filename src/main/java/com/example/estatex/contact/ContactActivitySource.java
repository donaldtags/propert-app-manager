package com.example.estatex.contact;

import java.util.List;

/**
 * Implemented by modules that record things against a contact (enquiries, viewings, offers,
 * deals) so the contact page shows one history without the contact module depending on them.
 */
public interface ContactActivitySource {
    List<ContactDtos.ActivityItem> activityFor(Long organisationId, Long contactId);
}
