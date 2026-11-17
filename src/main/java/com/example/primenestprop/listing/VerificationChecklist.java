package com.example.primenestprop.listing;

import com.example.primenestprop.property.TitleType;
import java.util.ArrayList;
import java.util.List;

/**
 * What must be checked before the agency marks a title VERIFIED. Depends on how the property is
 * held: a cession needs the chain of cessions traced, a sectional title needs the levy clearance.
 */
public final class VerificationChecklist {
    public record Item(String key, String label) {
    }

    private static final List<Item> COMMON = List.of(
            new Item("OWNER_ID_MATCHES", "Seller's ID matches the registered owner / cessionary"),
            new Item("RATES_CLEARANCE", "Council rates statement or clearance sighted"),
            new Item("NO_ENCUMBRANCES", "No bond, caveat or attachment registered against it"));

    private VerificationChecklist() {
    }

    public static List<Item> itemsFor(TitleType type) {
        List<Item> items = new ArrayList<>();
        if (type == null) {
            return items;
        }
        switch (type) {
            case TITLE_DEED -> {
                items.add(new Item("TITLE_DEED_SIGHTED", "Original title deed sighted"));
                items.add(new Item("DEEDS_SEARCH", "Deeds Office search done"));
            }
            case SECTIONAL_TITLE -> {
                items.add(new Item("SECTIONAL_TITLE_SIGHTED", "Sectional title deed sighted"));
                items.add(new Item("DEEDS_SEARCH", "Deeds Office search done"));
                items.add(new Item("LEVY_CLEARANCE", "Body corporate levy clearance sighted"));
            }
            case CESSION -> {
                items.add(new Item("CESSION_AGREEMENT_SIGHTED", "Cession agreement sighted"));
                items.add(new Item("DEVELOPER_CONFIRMATION", "Developer / land authority confirms the cession in writing"));
                items.add(new Item("CESSION_CHAIN", "Full chain of cessions traced back to the original allocation"));
            }
        }
        items.addAll(COMMON);
        return items;
    }
}
