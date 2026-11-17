package com.example.primenestprop.organisation;

/** Capabilities inside an organisation workspace. Checks test a permission, never a role. */
public enum OrgPermission {
    ORG_VIEW,
    ORG_SETTINGS_EDIT,
    MEMBERS_VIEW,
    MEMBERS_MANAGE,
    AUDIT_VIEW,

    LISTING_CREATE,
    LISTING_EDIT,
    /** Confirm ownership/title documents were checked. ORG_ADMIN and PREA. */
    TITLE_VERIFY,
    /** PREA only. */
    LISTING_APPROVE_PUBLISH,
    /** PREA only. */
    MANDATE_SIGN_OFF,

    TRUST_LEDGER_VIEW,
    TRUST_LEDGER_WRITE,
    COMMISSION_CONFIRM,
    MAINTENANCE_MANAGE,
    MARKET_DATA_WRITE,
    MARKET_DATA_ACHIEVED_VIEW,
    TARGETS_EDIT,

    OWNER_PORTAL_VIEW,
    TENANT_PORTAL_VIEW,
    BUYER_PORTAL_VIEW,
    INVESTOR_PORTAL_VIEW,
    LANDOWNER_PORTAL_VIEW
}
