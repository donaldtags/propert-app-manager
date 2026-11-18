package com.example.estatex.property;

import com.example.estatex.user.AppUser;
import java.util.List;

/**
 * Published (synchronously, inside the editing transaction) when an organisation listing's price,
 * address or title type changes, so PREA approval can be withdrawn.
 */
public record PropertyMaterialChangeEvent(Long propertyId, List<String> changedFields, AppUser actor) {
}
