package com.example.primenestprop.organisation;

import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the ESTATEX Group workspace on first boot, with all five divisions and its brand.
 * Reference data, not demo data, so it runs in every environment. Only ever inserts: once the
 * row exists, edits made in the app (settings page) are never overwritten. Members (ORG_ADMIN,
 * PREA) are added afterwards by a platform admin via POST /api/v1/orgs/estatex/members.
 */
@Component
@Order(5)
class EstatexOrganisationSeeder implements CommandLineRunner {
    static final String SLUG = "estatex";
    private static final Logger log = LoggerFactory.getLogger(EstatexOrganisationSeeder.class);

    private final OrganisationRepository organisations;

    EstatexOrganisationSeeder(OrganisationRepository organisations) {
        this.organisations = organisations;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (organisations.findBySlugIgnoreCase(SLUG).isPresent()) {
            return;
        }
        Organisation o = new Organisation();
        o.setSlug(SLUG);
        o.setName("ESTATEX Group");
        o.setSlogan("More Than Property. Building Possibilities.");
        o.setDivisions(EnumSet.allOf(Division.class));
        o.setLogoOnLightUrl("/orgs/estatex/logo-on-light.svg");
        o.setLogoOnDarkUrl("/orgs/estatex/logo-on-dark.svg");
        o.setPhone("+263 78 454 2297");
        o.setWhatsappNumber("263784542297");
        o.setInstagramHandle("estatexgroup");
        o.setAddress("Harare, Zimbabwe");
        o.setDefaultCurrency("USD");
        o.setTimezone("Africa/Harare");
        o.setSaleCommissionBps(500);
        o.setManagementFeeBps(1000);
        o.setLettingFeeMonthsX100(100);

        OrgTheme t = o.getTheme();
        t.setLightPrimary("#0B1D32");
        t.setLightSurface("#F5F2EA");
        t.setLightText("#0B1D32");
        t.setLightMuted("#5E6873");
        t.setLightAccent("#C9CED4");
        t.setDarkPrimary("#C9CED4");
        t.setDarkSurface("#0B1D32");
        t.setDarkText("#F5F2EA");
        t.setDarkMuted("#A3ABB5");
        t.setDarkAccent("#788391");
        t.setHeadingFont("Montserrat");
        t.setBodyFont("Inter");

        organisations.save(o);
        log.info("Seeded organisation workspace '{}'", SLUG);
    }
}
