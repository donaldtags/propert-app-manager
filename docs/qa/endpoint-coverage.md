# Endpoint coverage matrix

Generated 2026-11-27T07:04:20.900Z by `qa-automation/tools/inventory.mjs` from the controllers and SecurityConfig. ✅ = at least one successful (2xx) call, ⛔ = only rejected/error answers (status codes shown), — = never called. Counts are the number of requests the last measured run actually sent to that endpoint (from the backend's api_request log).

| Module | Method | Path | Handler | Access (URL rule) | Backend tests | QA suite |
|---|---|---|---|---|---|---|
| admin | GET | `/api/v1/admin/fraud-signals` | AdminFraudController.fraudSignals | PERM_FRAUD_SIGNALS_VIEW | — | ✅ 4 |
| agent | POST | `/api/v1/agents/licence` | AgentLicenceController.submit | signed-in | ✅ 3 | ✅ 2 |
| agent | GET | `/api/v1/agents/licence/me` | AgentLicenceController.mine | signed-in | — | ✅ 3 |
| agent | PATCH | `/api/v1/agents/licences/{id}` | AgentLicenceController.review | signed-in | ✅ 5 | ✅ 2 |
| agent | GET | `/api/v1/agents/licences/{id}/certificate` | AgentLicenceController.certificate | signed-in | — | ⛔ 1 (404) |
| agent | GET | `/api/v1/agents/licences/pending` | AgentLicenceController.pending | signed-in | — | ✅ 4 |
| ai | POST | `/api/v1/ai/affordability` | AiAssistantController.affordability | public | — | ⛔ 3 (400) |
| ai | POST | `/api/v1/ai/home-assistant` | AiAssistantController.homeAssistant | signed-in | — | ⛔ 1 (400) |
| ai | GET | `/api/v1/ai/lease-explanation/{leaseId}` | AiAssistantController.leaseExplanation | signed-in | — | ⛔ 1 (404) |
| ai | POST | `/api/v1/ai/property-search` | AiAssistantController.propertySearch | public | ✅ 1 | ✅ 4 |
| ai | GET | `/api/v1/ai/rent-suggestion` | AiAssistantController.rentSuggestion | signed-in | — | ⛔ 1 (400) |
| analytics/api | GET | `/api/v1/admin/analytics/definitions` | AnalyticsEngineController.definitions | signed-in | ✅ 1 | ⛔ 1 (403) |
| analytics/api | POST | `/api/v1/admin/analytics/engine/rebuild` | AnalyticsEngineController.rebuild | signed-in | ⛔ 1 (400) | ⛔ 1 (400) |
| analytics/api | POST | `/api/v1/admin/analytics/engine/run` | AnalyticsEngineController.runNow | signed-in | ⛔ 1 (403) | ✅ 2 |
| analytics/api | GET | `/api/v1/admin/analytics/engine/runs` | AnalyticsEngineController.runs | signed-in | ✅ 2 | ✅ 4 |
| analytics/api | GET | `/api/v1/admin/analytics/engine/status` | AnalyticsEngineController.engineStatus | signed-in | ✅ 2 | ✅ 4 |
| analytics/api | GET | `/api/v1/admin/analytics/metrics` | AnalyticsEngineController.series | signed-in | ⛔ 1 (403) | ⛔ 1 (400) |
| analytics/api | GET | `/api/v1/admin/analytics/overview` | AnalyticsEngineController.overview | signed-in | ✅ 12 | ✅ 5 |
| analytics/api | GET | `/api/v1/admin/analytics/records` | AnalyticsEngineController.records | signed-in | ✅ 6 | ⛔ 1 (400) |
| analytics/capture | POST | `/api/v1/marketplace-events` | MarketplaceEventController.track | public | ✅ 30 | ✅ 37 |
| analytics/capture | GET | `/api/v1/saved-properties` | SavedPropertyController.list | signed-in | ✅ 1 | ✅ 1 |
| analytics/capture | PUT | `/api/v1/saved-properties/{propertyId}` | SavedPropertyController.save | signed-in | ✅ 2 | ⛔ 1 (404) |
| analytics/capture | DELETE | `/api/v1/saved-properties/{propertyId}` | SavedPropertyController.unsave | signed-in | ✅ 1 | ✅ 1 |
| analytics/capture | GET | `/api/v1/saved-properties/ids` | SavedPropertyController.ids | signed-in | ✅ 4 | ✅ 1 |
| analytics/insight | GET | `/api/v1/admin/analytics/alert-preferences` | InsightsController.preferences | signed-in | ✅ 1 | ✅ 3 |
| analytics/insight | PUT | `/api/v1/admin/analytics/alert-preferences/{key}` | InsightsController.savePreference | signed-in | ✅ 2 | ⛔ 1 (400) |
| analytics/insight | GET | `/api/v1/admin/analytics/alert-rules` | InsightsController.rules | signed-in | ✅ 1 | ✅ 3 |
| analytics/insight | PUT | `/api/v1/admin/analytics/alert-rules/{key}` | InsightsController.saveRule | signed-in | ✅ 2 | ⛔ 1 (400) |
| analytics/insight | GET | `/api/v1/admin/analytics/alerts` | InsightsController.alerts | signed-in | ✅ 3 | ✅ 3 |
| analytics/insight | POST | `/api/v1/admin/analytics/alerts/{id}/acknowledge` | InsightsController.acknowledge | signed-in | ✅ 2 | ⛔ 1 (403) |
| analytics/insight | GET | `/api/v1/admin/analytics/insights` | InsightsController.insights | signed-in | ✅ 6 | ✅ 4 |
| analytics/insight | PUT | `/api/v1/admin/analytics/insights/ai` | InsightsController.toggleAi | signed-in | ✅ 2 | ⛔ 1 (400) |
| analytics/market | GET | `/api/v1/admin/analytics/demand` | GeoDemandController.demand | signed-in | ✅ 5 | ✅ 3 |
| analytics/market | GET | `/api/v1/admin/analytics/geo/{type}` | GeoDemandController.map | signed-in | ✅ 7 | ✅ 3 |
| analytics/market | GET | `/api/v1/admin/analytics/market/{type}` | MarketDashboardController.dashboard | signed-in | ✅ 14 | ✅ 7 |
| analytics/market | GET | `/api/v1/admin/analytics/market/{type}/listings` | MarketDashboardController.listings | signed-in | ✅ 1 | ⛔ 1 (403) |
| analytics/market | GET | `/api/v1/admin/analytics/market/settings/yield-cost-ratio` | MarketDashboardController.costRatio | signed-in | — | ⛔ 1 (403) |
| analytics/market | PUT | `/api/v1/admin/analytics/market/settings/yield-cost-ratio` | MarketDashboardController.setCostRatio | signed-in | ✅ 3 | ⛔ 1 (403) |
| analytics/market | POST | `/api/v1/admin/analytics/marketing-spend` | RevenueController.addSpend | signed-in | ✅ 2 | ⛔ 1 (400) |
| analytics/market | DELETE | `/api/v1/admin/analytics/marketing-spend/{id}` | RevenueController.deleteSpend | signed-in | — | ⛔ 1 (403) |
| analytics/market | GET | `/api/v1/admin/analytics/revenue` | RevenueController.revenue | signed-in | ✅ 7 | ✅ 3 |
| analytics/market | PUT | `/api/v1/admin/analytics/revenue/commission-share` | RevenueController.commissionShare | signed-in | ✅ 5 | ⛔ 1 (400) |
| analytics/report | GET | `/api/v1/admin/analytics/report-recipients` | AnalyticsReportController.recipients | signed-in | ✅ 1 | ⛔ 1 (400) |
| analytics/report | GET | `/api/v1/admin/analytics/report-schedules` | AnalyticsReportController.schedules | signed-in | ✅ 1 | ✅ 3 |
| analytics/report | POST | `/api/v1/admin/analytics/report-schedules` | AnalyticsReportController.create | signed-in | ✅ 4 | ⛔ 1 (400) |
| analytics/report | PUT | `/api/v1/admin/analytics/report-schedules/{id}` | AnalyticsReportController.update | signed-in | — | ⛔ 1 (400) |
| analytics/report | DELETE | `/api/v1/admin/analytics/report-schedules/{id}` | AnalyticsReportController.delete | signed-in | ✅ 1 | ⛔ 1 (403) |
| analytics/report | POST | `/api/v1/admin/analytics/report-schedules/{id}/send-now` | AnalyticsReportController.sendNow | signed-in | ✅ 1 | ⛔ 1 (403) |
| analytics/report | GET | `/api/v1/admin/analytics/reports` | AnalyticsReportController.catalogue | signed-in | ✅ 5 | ✅ 3 |
| analytics/report | GET | `/api/v1/admin/analytics/reports/{type}` | AnalyticsReportController.download | signed-in | ✅ 26 | ✅ 6 |
| analytics/report | GET | `/api/v1/admin/analytics/reports/runs` | AnalyticsReportController.runs | signed-in | — | — |
| analytics/report | GET | `/api/v1/admin/analytics/saved-views` | AnalyticsReportController.savedViews | signed-in | ✅ 4 | ✅ 6 |
| analytics/report | POST | `/api/v1/admin/analytics/saved-views` | AnalyticsReportController.createView | signed-in | ✅ 3 | ✅ 2 |
| analytics/report | PUT | `/api/v1/admin/analytics/saved-views/{id}` | AnalyticsReportController.updateView | signed-in | ✅ 1 | ⛔ 1 (400) |
| analytics/report | DELETE | `/api/v1/admin/analytics/saved-views/{id}` | AnalyticsReportController.deleteView | signed-in | ✅ 2 | ⛔ 1 (403) |
| application | POST | `/api/v1/applications` | RentalApplicationController.create | signed-in | — | ⛔ 1 (400) |
| application | GET | `/api/v1/applications/{id}` | RentalApplicationController.get | signed-in | — | ✅ 3 |
| application | PATCH | `/api/v1/applications/{id}/review` | RentalApplicationController.review | signed-in | — | ⛔ 1 (400) |
| application | PATCH | `/api/v1/applications/{id}/submit` | RentalApplicationController.submit | signed-in | — | ⛔ 1 (404) |
| application | GET | `/api/v1/applications/mine` | RentalApplicationController.mine | signed-in | — | — |
| application | GET | `/api/v1/applications/received` | RentalApplicationController.received | signed-in | — | — |
| auth | GET | `/api/v1/auth/2fa` | AuthController.twoFactorStatus | signed-in | — | ✅ 13 |
| auth | POST | `/api/v1/auth/2fa/disable` | AuthController.disable | signed-in | ✅ 2 | ⛔ 1 (400) |
| auth | POST | `/api/v1/auth/2fa/disable/code` | AuthController.sendDisableCode | signed-in | — | ⛔ 1 (503) |
| auth | POST | `/api/v1/auth/2fa/sms/confirm` | AuthController.confirmSms | signed-in | ✅ 1 | ⛔ 1 (400) |
| auth | POST | `/api/v1/auth/2fa/sms/send` | AuthController.sendLoginCode | public | — | ⛔ 3 (400) |
| auth | POST | `/api/v1/auth/2fa/sms/start` | AuthController.startSms | signed-in | ✅ 3 | ⛔ 1 (503) |
| auth | POST | `/api/v1/auth/2fa/totp/confirm` | AuthController.confirmTotp | signed-in | ✅ 7 | ✅ 2 |
| auth | POST | `/api/v1/auth/2fa/totp/start` | AuthController.startTotp | signed-in | ✅ 5 | ✅ 2 |
| auth | POST | `/api/v1/auth/2fa/verify` | AuthController.verifyTwoFactor | public | ✅ 13 | ✅ 5 |
| auth | POST | `/api/v1/auth/change-password` | AuthController.changePassword | signed-in | ✅ 2 | ⛔ 1 (400) |
| auth | POST | `/api/v1/auth/forgot-password` | AuthController.forgotPassword | public | ✅ 3 | ⛔ 3 (400) |
| auth | POST | `/api/v1/auth/login` | AuthController.login | public | ✅ 164 | ✅ 19 |
| auth | POST | `/api/v1/auth/logout` | AuthController.logout | signed-in | ✅ 1 | — |
| auth | POST | `/api/v1/auth/logout-all` | AuthController.logoutAll | signed-in | ✅ 1 | — |
| auth | GET | `/api/v1/auth/me` | AuthController.me | signed-in | ✅ 6 | ✅ 142 |
| auth | POST | `/api/v1/auth/register` | AuthController.register | public | ✅ 298 | ✅ 71 |
| auth | POST | `/api/v1/auth/reset-password` | AuthController.resetPassword | public | ✅ 3 | ⛔ 3 (400) |
| campaign | PUT | `/api/v1/orgs/{slug}/campaign-tasks/{taskId}` | CampaignController.setTask | signed-in | ✅ 2 | ✅ 2 |
| campaign | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/campaign` | CampaignController.current | signed-in | ✅ 1 | ✅ 10 |
| campaign | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/campaign` | CampaignController.start | signed-in | ✅ 3 | ✅ 2 |
| chat | POST | `/api/v1/messages` | ChatController.send | signed-in | ✅ 1 | ⛔ 1 (400) |
| chat | GET | `/api/v1/messages/admin/conversations` | ChatController.adminConversations | PERM_MESSAGES_ADMIN_VIEW | — | ✅ 4 |
| chat | GET | `/api/v1/messages/admin/conversations/{conversationId}` | ChatController.adminThread | PERM_MESSAGES_ADMIN_VIEW | — | — |
| chat | GET | `/api/v1/messages/conversations` | ChatController.conversations | signed-in | ✅ 1 | ✅ 13 |
| chat | POST | `/api/v1/messages/conversations` | ChatController.start | signed-in | ✅ 1 | ⛔ 1 (400) |
| chat | GET | `/api/v1/messages/conversations/{conversationId}` | ChatController.thread | signed-in | ✅ 1 | ⛔ 1 (404) |
| chat | PATCH | `/api/v1/messages/conversations/{conversationId}/read` | ChatController.markRead | signed-in | ✅ 1 | ⛔ 1 (404) |
| commission | GET | `/api/v1/orgs/{slug}/commissions` | CommissionController.list | signed-in | — | ⛔ 1 (404) |
| commission | POST | `/api/v1/orgs/{slug}/commissions` | CommissionController.create | signed-in | ✅ 1 | ⛔ 1 (400) |
| commission | POST | `/api/v1/orgs/{slug}/commissions/{id}/cancel` | CommissionController.cancel | signed-in | — | ⛔ 1 (404) |
| commission | POST | `/api/v1/orgs/{slug}/commissions/{id}/conveyancer-confirmation` | CommissionController.confirm | signed-in | ✅ 3 | ⛔ 1 (400) |
| commission | POST | `/api/v1/orgs/{slug}/commissions/{id}/received` | CommissionController.received | signed-in | ✅ 2 | ⛔ 1 (400) |
| common | POST | `/api/v1/logs/frontend` | FrontendLogController.frontend | public | — | ✅ 18 |
| common | GET | `/api/v1/version` | VersionController.version | public | — | ✅ 5 |
| construction | POST | `/api/v1/construction-projects` | ConstructionController.create | signed-in | — | ⛔ 1 (400) |
| construction | GET | `/api/v1/construction-projects` | ConstructionController.list | signed-in | — | ⛔ 1 (400) |
| construction | GET | `/api/v1/construction-projects/{id}` | ConstructionController.get | signed-in | — | ⛔ 1 (404) |
| construction | PATCH | `/api/v1/construction-projects/{id}` | ConstructionController.update | signed-in | — | ⛔ 1 (404) |
| construction | POST | `/api/v1/construction-projects/{id}/milestones` | ConstructionController.addMilestone | signed-in | — | ⛔ 1 (400) |
| construction | GET | `/api/v1/construction-projects/{id}/milestones` | ConstructionController.listMilestones | signed-in | — | ⛔ 1 (404) |
| construction | POST | `/api/v1/construction-projects/{id}/updates` | ConstructionController.postUpdate | signed-in | — | ⛔ 1 (400) |
| construction | GET | `/api/v1/construction-projects/{id}/updates` | ConstructionController.listUpdates | signed-in | — | ⛔ 1 (404) |
| construction | PATCH | `/api/v1/construction-projects/milestones/{milestoneId}/approve` | ConstructionController.approveMilestone | signed-in | — | ⛔ 1 (404) |
| construction | PATCH | `/api/v1/construction-projects/milestones/{milestoneId}/mark-paid` | ConstructionController.markMilestonePaid | signed-in | — | ⛔ 1 (404) |
| construction | PATCH | `/api/v1/construction-projects/milestones/{milestoneId}/submit` | ConstructionController.submitMilestone | signed-in | — | ⛔ 1 (404) |
| contact | GET | `/api/v1/orgs/{slug}/contacts` | ContactController.search | signed-in | ✅ 1 | ✅ 20 |
| contact | POST | `/api/v1/orgs/{slug}/contacts` | ContactController.create | signed-in | ✅ 9 | ✅ 9 |
| contact | GET | `/api/v1/orgs/{slug}/contacts/{id}` | ContactController.detail | signed-in | ✅ 3 | ⛔ 1 (404) |
| contact | PATCH | `/api/v1/orgs/{slug}/contacts/{id}` | ContactController.update | signed-in | — | ⛔ 1 (404) |
| contact | PUT | `/api/v1/orgs/{slug}/contacts/{id}/qualification` | ContactController.qualify | signed-in | ✅ 2 | ⛔ 1 (404) |
| dashboard | GET | `/api/v1/dashboards/admin` | DashboardController.admin | PERM_DASHBOARD_ADMIN_VIEW | ✅ 1 | ✅ 4 |
| dashboard | GET | `/api/v1/dashboards/diaspora/{userId}` | DashboardController.diaspora | signed-in | — | ⛔ 1 (403) |
| dashboard | GET | `/api/v1/dashboards/landlords/{landlordId}` | DashboardController.landlord | signed-in | ✅ 1 | ✅ 15 |
| dashboard | GET | `/api/v1/dashboards/tenants/{tenantId}` | DashboardController.tenant | signed-in | ✅ 1 | ✅ 21 |
| enquiry | GET | `/api/v1/orgs/{slug}/enquiries` | EnquiryController.list | signed-in | ✅ 3 | ✅ 19 |
| enquiry | POST | `/api/v1/orgs/{slug}/enquiries` | EnquiryController.capture | signed-in | ✅ 9 | ✅ 5 |
| enquiry | PATCH | `/api/v1/orgs/{slug}/enquiries/{id}/status` | EnquiryController.status | signed-in | — | ⛔ 1 (400) |
| escrow | POST | `/api/v1/escrows` | EscrowController.create | signed-in | ✅ 2 | ⛔ 1 (400) |
| escrow | GET | `/api/v1/escrows` | EscrowController.list | signed-in | ✅ 1 | ⛔ 1 (400) |
| escrow | POST | `/api/v1/escrows/{id}/checkout` | EscrowController.checkout | signed-in | — | ⛔ 1 (400) |
| escrow | PATCH | `/api/v1/escrows/{id}/dispute` | EscrowController.dispute | signed-in | — | ⛔ 1 (404) |
| escrow | PATCH | `/api/v1/escrows/{id}/fund` | EscrowController.fund | signed-in | ✅ 2 | ⛔ 1 (400) |
| escrow | PATCH | `/api/v1/escrows/{id}/release` | EscrowController.release | PERM_ESCROW_RELEASE | — | — |
| escrow | GET | `/api/v1/escrows/admin` | EscrowController.allForAdmin | PERM_ESCROW_ADMIN_VIEW | — | ✅ 4 |
| featured | GET | `/api/v1/featured-listings/settings` | FeaturedListingController.settings | public | — | ✅ 7 |
| featured | PUT | `/api/v1/featured-listings/settings` | FeaturedListingController.updateSettings | PERM_FEATURED_LISTINGS_MANAGE | — | — |
| featured | POST | `/api/v1/properties/{id}/feature/checkout` | FeaturedListingController.featureCheckout | signed-in | — | ⛔ 1 (400) |
| intake | GET | `/api/v1/orgs/{slug}/inspection-photos/{photoId}` | IntakeController.photo | signed-in | ✅ 1 | ⛔ 1 (404) |
| intake | DELETE | `/api/v1/orgs/{slug}/inspection-photos/{photoId}` | IntakeController.deletePhoto | signed-in | — | ⛔ 1 (404) |
| intake | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/inspection` | IntakeController.inspection | signed-in | ✅ 2 | ✅ 5 |
| intake | PUT | `/api/v1/orgs/{slug}/listings/{propertyId}/inspection` | IntakeController.saveInspection | signed-in | ✅ 3 | ⛔ 1 (400) |
| intake | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/inspection/photos` | IntakeController.addPhoto | signed-in | ✅ 2 | ⛔ 1 (500) |
| intake | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/pricing` | IntakeController.pricing | signed-in | ✅ 2 | ✅ 5 |
| intake | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/pricing` | IntakeController.saveRecommendation | signed-in | ✅ 1 | ⛔ 1 (400) |
| investment | GET | `/api/v1/investments` | InvestmentController.list | signed-in | ✅ 1 | ✅ 3 |
| investment | POST | `/api/v1/investments/checkout` | InvestmentController.checkout | signed-in | ✅ 2 | ⛔ 1 (400) |
| investment | GET | `/api/v1/investments/fee-quote` | InvestmentController.feeQuote | public | ✅ 2 | ✅ 11 |
| investment | POST | `/api/v1/investments/reits` | InvestmentController.createReit | PERM_INVESTMENT_MANAGE | ✅ 1 | — |
| investment | GET | `/api/v1/investments/reits` | InvestmentController.reits | public | ✅ 2 | ✅ 14 |
| investment | PATCH | `/api/v1/investments/reits/{id}/properties` | InvestmentController.updateReitProperties | PERM_INVESTMENT_MANAGE | — | — |
| investment | PATCH | `/api/v1/investments/sell` | InvestmentController.sell | signed-in | ✅ 1 | ⛔ 1 (400) |
| kyc | GET | `/api/v1/kyc/checks/available` | KycController.availableChecks | signed-in | ✅ 1 | ✅ 1 |
| kyc | POST | `/api/v1/kyc/document-check` | KycController.startDocumentCheck | signed-in | ✅ 1 | ⛔ 1 (503) |
| kyc | POST | `/api/v1/kyc/submissions` | KycController.submit | signed-in | ✅ 24 | ✅ 3 |
| kyc | GET | `/api/v1/kyc/submissions` | KycController.listForAdmin | PERM_KYC_REVIEW | ✅ 10 | ✅ 9 |
| kyc | GET | `/api/v1/kyc/submissions/{id}` | KycController.get | signed-in | — | ⛔ 1 (404) |
| kyc | POST | `/api/v1/kyc/submissions/{id}/registry-check` | KycController.recheckRegistry | signed-in | ⛔ 1 (409) | ⛔ 1 (403) |
| kyc | PATCH | `/api/v1/kyc/submissions/{id}/review` | KycController.review | PERM_KYC_REVIEW | ✅ 24 | ✅ 2 |
| kyc | GET | `/api/v1/kyc/submissions/{submissionId}/documents/{documentId}/download` | KycController.downloadDocument | signed-in | — | ✅ 9 |
| kyc | GET | `/api/v1/kyc/submissions/me` | KycController.mine | signed-in | — | ✅ 1 |
| kyc | POST | `/api/v1/kyc/webhooks/didit` | KycController.diditWebhook | public | ✅ 3 | ⛔ 3 (503) |
| lease | POST | `/api/v1/leases` | LeaseController.create | signed-in | ✅ 3 | ⛔ 1 (400) |
| lease | GET | `/api/v1/leases` | LeaseController.list | signed-in | ✅ 1 | ✅ 13 |
| lease | PATCH | `/api/v1/leases/{id}/sign` | LeaseController.sign | signed-in | ✅ 4 | ⛔ 1 (404) |
| lease | POST | `/api/v1/leases/{leaseId}/actions` | LeaseController.requestAction | signed-in | — | ⛔ 1 (400) |
| lease | POST | `/api/v1/leases/{leaseId}/documents` | LeaseController.uploadDocuments | signed-in | ✅ 1 | ⛔ 1 (500) |
| lease | GET | `/api/v1/leases/{leaseId}/documents` | LeaseController.listDocuments | signed-in | ✅ 1 | ⛔ 1 (404) |
| lease | GET | `/api/v1/leases/{leaseId}/documents/{documentId}/download` | LeaseController.downloadDocument | signed-in | ✅ 1 | ⛔ 1 (404) |
| lease | PATCH | `/api/v1/leases/{leaseId}/documents/{documentId}/review` | LeaseController.reviewDocument | signed-in | ✅ 1 | ⛔ 1 (400) |
| lease | PATCH | `/api/v1/leases/actions/{id}/review` | LeaseController.reviewAction | signed-in | — | ⛔ 1 (400) |
| lease | GET | `/api/v1/leases/actions/mine` | LeaseController.myActions | signed-in | — | ✅ 21 |
| lease | GET | `/api/v1/leases/actions/received` | LeaseController.receivedActions | signed-in | — | ✅ 15 |
| lease | POST | `/api/v1/leases/extract` | LeaseController.extract | signed-in | — | ⛔ 1 (500) |
| listing | GET | `/api/v1/orgs/{slug}/listings` | OrgListingController.list | signed-in | ✅ 1 | ✅ 44 |
| listing | POST | `/api/v1/orgs/{slug}/listings` | OrgListingController.create | signed-in | ✅ 22 | ✅ 17 |
| listing | GET | `/api/v1/orgs/{slug}/listings/{propertyId}` | OrgListingController.detail | signed-in | ✅ 10 | ✅ 26 |
| listing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/approve` | OrgListingController.approve | signed-in | ✅ 16 | ✅ 14 |
| listing | PUT | `/api/v1/orgs/{slug}/listings/{propertyId}/consent` | OrgListingController.consent | signed-in | ✅ 13 | ✅ 12 |
| listing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/mandates` | OrgListingController.createMandate | signed-in | ✅ 11 | ✅ 11 |
| listing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/publish` | OrgListingController.publish | signed-in | ✅ 18 | ✅ 12 |
| listing | PUT | `/api/v1/orgs/{slug}/listings/{propertyId}/title` | OrgListingController.setTitle | signed-in | ✅ 12 | ✅ 13 |
| listing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/title-verification` | OrgListingController.verifyTitle | signed-in | ✅ 13 | ✅ 12 |
| listing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/unpublish` | OrgListingController.unpublish | signed-in | — | ⛔ 1 (404) |
| listing | PUT | `/api/v1/orgs/{slug}/listings/{propertyId}/verification-checklist` | OrgListingController.checklist | signed-in | ✅ 13 | ✅ 11 |
| listing | POST | `/api/v1/orgs/{slug}/mandates/{mandateId}/cancel` | OrgListingController.cancel | signed-in | ✅ 1 | ⛔ 1 (404) |
| listing | POST | `/api/v1/orgs/{slug}/mandates/{mandateId}/sign-off` | OrgListingController.signOff | signed-in | ✅ 14 | ✅ 11 |
| listing | POST | `/api/v1/orgs/{slug}/mandates/{mandateId}/signed-document` | OrgListingController.attachSignedDocument | signed-in | ✅ 11 | ✅ 11 |
| listing | GET | `/api/v1/orgs/{slug}/mandates/{mandateId}/signed-document` | OrgListingController.document | signed-in | — | ⛔ 1 (404) |
| location | GET | `/api/v1/locations` | LocationController.all | public | ✅ 3 | ✅ 21 |
| location | PATCH | `/api/v1/locations/{id}` | LocationController.update | signed-in | ✅ 2 | ⛔ 1 (403) |
| location | DELETE | `/api/v1/locations/{id}` | LocationController.reject | signed-in | — | ⛔ 1 (403) |
| location | GET | `/api/v1/locations/pending` | LocationController.pending | signed-in | — | ⛔ 1 (403) |
| location | POST | `/api/v1/locations/suggestions` | LocationController.suggest | signed-in | ✅ 2 | ⛔ 1 (400) |
| maintenance | POST | `/api/v1/maintenance` | MaintenanceController.create | signed-in | ✅ 3 | ⛔ 1 (400) |
| maintenance | GET | `/api/v1/maintenance` | MaintenanceController.list | signed-in | ✅ 1 | ⛔ 1 (400) |
| maintenance | PATCH | `/api/v1/maintenance/{id}/assign-vendor` | MaintenanceController.assignVendor | signed-in | — | ⛔ 1 (400) |
| maintenance | POST | `/api/v1/maintenance/{id}/photos` | MaintenanceController.uploadPhotos | signed-in | — | ⛔ 1 (500) |
| maintenance | GET | `/api/v1/maintenance/{id}/photos` | MaintenanceController.listPhotos | signed-in | — | ⛔ 1 (404) |
| maintenance | PATCH | `/api/v1/maintenance/{id}/status` | MaintenanceController.updateStatus | signed-in | ✅ 2 | ⛔ 1 (400) |
| market | GET | `/api/v1/market/reits/zw` | MarketController.zimbabweReits | public | — | ✅ 12 |
| neighbourhood | GET | `/api/v1/neighbourhoods` | NeighbourhoodController.get | public | — | ⛔ 3 (400) |
| neighbourhood | PUT | `/api/v1/neighbourhoods` | NeighbourhoodController.upsert | PERM_NEIGHBOURHOOD_MANAGE | — | — |
| notification | PATCH | `/api/v1/notifications/{id}/read` | NotificationController.markRead | signed-in | — | ⛔ 1 (404) |
| notification | GET | `/api/v1/notifications/mine` | NotificationController.mine | signed-in | ✅ 2 | ✅ 1 |
| notification | PATCH | `/api/v1/notifications/read-all` | NotificationController.markAllRead | signed-in | — | ✅ 1 |
| notification | GET | `/api/v1/notifications/unread-count` | NotificationController.unreadCount | signed-in | ✅ 1 | ✅ 295 |
| offer | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/offers` | OfferController.list | signed-in | — | ✅ 8 |
| offer | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/offers` | OfferController.record | signed-in | ✅ 3 | ✅ 4 |
| offer | POST | `/api/v1/orgs/{slug}/offers/{offerId}/owner-response` | OfferController.respond | signed-in | ✅ 4 | ✅ 4 |
| offer | POST | `/api/v1/orgs/{slug}/offers/{offerId}/present` | OfferController.present | signed-in | ✅ 1 | ⛔ 1 (400) |
| offer | POST | `/api/v1/orgs/{slug}/offers/{offerId}/withdraw` | OfferController.withdraw | signed-in | ✅ 1 | ⛔ 1 (404) |
| organisation | GET | `/api/v1/orgs` | OrganisationController.directory | public | ✅ 2 | ✅ 6 |
| organisation | POST | `/api/v1/orgs` | OrganisationController.create | signed-in | ✅ 25 | ✅ 9 |
| organisation | GET | `/api/v1/orgs/{slug}` | OrganisationController.get | public | ✅ 4 | ✅ 11 |
| organisation | PATCH | `/api/v1/orgs/{slug}` | OrganisationController.update | signed-in | ✅ 4 | ⛔ 2 (403/404) |
| organisation | PATCH | `/api/v1/orgs/{slug}/approval` | OrganisationController.decide | signed-in | ✅ 2 | ✅ 2 |
| organisation | GET | `/api/v1/orgs/{slug}/audit` | OrganisationController.audit | signed-in | ✅ 8 | ⛔ 3 (403/404) |
| organisation | GET | `/api/v1/orgs/{slug}/me` | OrganisationController.me | signed-in | ✅ 4 | ✅ 163 |
| organisation | GET | `/api/v1/orgs/{slug}/members` | OrganisationController.members | signed-in | ✅ 2 | ⛔ 2 (403/404) |
| organisation | POST | `/api/v1/orgs/{slug}/members` | OrganisationController.addMember | signed-in | ✅ 61 | ✅ 22 |
| organisation | PATCH | `/api/v1/orgs/{slug}/members/{membershipId}` | OrganisationController.updateMember | signed-in | ⛔ 1 (409) | ⛔ 1 (404) |
| organisation | GET | `/api/v1/orgs/{slug}/public-listings` | OrganisationController.publicListings | public | ✅ 1 | ✅ 8 |
| organisation | GET | `/api/v1/orgs/mine` | OrganisationController.mine | public | ✅ 2 | ✅ 3 |
| organisation | GET | `/api/v1/orgs/pending-review` | OrganisationController.pendingReview | public | — | ✅ 6 |
| payment | POST | `/api/v1/payments` | PaymentController.create | signed-in | ✅ 3 | ⛔ 1 (400) |
| payment | GET | `/api/v1/payments` | PaymentController.list | signed-in | ✅ 1 | ✅ 13 |
| payment | POST | `/api/v1/payments/{id}/checkout` | PaymentController.checkout | signed-in | — | ⛔ 1 (400) |
| payment | GET | `/api/v1/payments/{id}/confirm-checkout` | PaymentController.confirmCheckout | signed-in | — | ⛔ 1 (404) |
| payment | GET | `/api/v1/payments/{id}/receipt` | PaymentController.receipt | signed-in | — | ⛔ 1 (404) |
| payment | PATCH | `/api/v1/payments/{id}/success` | PaymentController.success | signed-in | ✅ 2 | ⛔ 1 (404) |
| payment | GET | `/api/v1/payments/statement.csv` | PaymentController.statement | signed-in | — | ⛔ 1 (400) |
| payment/gateway | POST | `/api/v1/payments/webhooks/{provider}` | PaymentWebhookController.webhook | public | ✅ 14 | ✅ 3 |
| pipeline | GET | `/api/v1/orgs/{slug}/pipeline` | PipelineController.board | signed-in | — | ✅ 25 |
| pipeline | POST | `/api/v1/orgs/{slug}/pipeline` | PipelineController.create | signed-in | ✅ 1 | ✅ 3 |
| pipeline | PATCH | `/api/v1/orgs/{slug}/pipeline/{id}` | PipelineController.update | signed-in | ✅ 2 | ⛔ 1 (404) |
| pipeline | POST | `/api/v1/orgs/{slug}/pipeline/{id}/move` | PipelineController.move | signed-in | ✅ 8 | ✅ 7 |
| property | GET | `/api/v1/listing-reports` | ListingReportController.open | signed-in | ⛔ 1 (403) | ✅ 3 |
| property | PATCH | `/api/v1/listing-reports/{id}` | ListingReportController.resolve | signed-in | ✅ 2 | ⛔ 1 (403) |
| property | POST | `/api/v1/properties` | PropertyController.create | signed-in | ✅ 137 | ✅ 15 |
| property | GET | `/api/v1/properties` | PropertyController.search | public | ✅ 1 | ✅ 49 |
| property | GET | `/api/v1/properties/{id}` | PropertyController.get | public | ✅ 24 | ✅ 35 |
| property | PATCH | `/api/v1/properties/{id}` | PropertyController.update | signed-in | ✅ 26 | ✅ 3 |
| property | GET | `/api/v1/properties/{id}/billing` | PropertyController.billing | signed-in | — | ⛔ 1 (404) |
| property | POST | `/api/v1/properties/{id}/inquiries` | PropertyController.submitInquiry | public | ✅ 2 | ⛔ 3 (400) |
| property | GET | `/api/v1/properties/{id}/passport` | PropertyController.passport | public | ✅ 3 | ⛔ 3 (404) |
| property | POST | `/api/v1/properties/{id}/photos` | PropertyController.uploadPhotos | signed-in | ✅ 6 | ⛔ 2 (403/404) |
| property | POST | `/api/v1/properties/{id}/reports` | ListingReportController.report | public | ✅ 7 | ✅ 4 |
| property | PATCH | `/api/v1/properties/{id}/verify` | PropertyController.verify | PERM_PROPERTY_VERIFY | ✅ 9 | — |
| property | GET | `/api/v1/properties/geocode` | PropertyController.geocode | public | — | ✅ 3 |
| property | GET | `/api/v1/property-inquiries` | PropertyInquiryController.mine | signed-in | — | ✅ 15 |
| property | GET | `/uploads/property-photos/{propertyId}/{filename}` | PropertyPhotoRedirectController.serve | public | — | — |
| referral | GET | `/api/v1/admin/referrals` | ReferralController.admin | signed-in | ✅ 2 | ✅ 3 |
| referral | POST | `/api/v1/admin/referrals/{id}/reverse` | ReferralController.reverse | signed-in | ✅ 2 | ⛔ 1 (403) |
| referral | PUT | `/api/v1/admin/referrals/settings` | ReferralController.settings | signed-in | ✅ 13 | ⛔ 1 (403) |
| referral | GET | `/api/v1/referrals/code/{code}` | ReferralController.code | public | ✅ 3 | ✅ 3 |
| referral | GET | `/api/v1/referrals/mine` | ReferralController.mine | signed-in | ✅ 13 | ✅ 1 |
| report | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/seller-reports` | ReportController.list | signed-in | — | ✅ 10 |
| report | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/seller-reports` | ReportController.generate | signed-in | ✅ 1 | ✅ 2 |
| report | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/share-image.png` | ReportController.shareImage | signed-in | ✅ 1 | ✅ 2 |
| report | GET | `/api/v1/shared/seller-reports/{token}` | ReportController.shared | public | ✅ 2 | ✅ 5 |
| review | POST | `/api/v1/ratings` | LandlordRatingController.create | signed-in | ✅ 1 | ⛔ 1 (400) |
| review | GET | `/api/v1/ratings` | LandlordRatingController.list | public | ✅ 3 | ✅ 18 |
| showing | GET | `/api/v1/orgs/{slug}/listings/{propertyId}/viewings` | ShowingController.forListing | signed-in | — | ✅ 8 |
| showing | POST | `/api/v1/orgs/{slug}/listings/{propertyId}/viewings` | ShowingController.schedule | signed-in | ✅ 7 | ✅ 3 |
| showing | GET | `/api/v1/orgs/{slug}/viewings` | ShowingController.upcoming | signed-in | — | ✅ 17 |
| showing | PUT | `/api/v1/orgs/{slug}/viewings/{id}/outcome` | ShowingController.outcome | signed-in | ✅ 3 | ⛔ 1 (400) |
| stay | GET | `/api/v1/stays/{propertyId}` | StayController.settings | public | ✅ 2 | ✅ 40 |
| stay | GET | `/api/v1/stays/{propertyId}/availability` | StayController.availability | public | ✅ 3 | ✅ 23 |
| stay | POST | `/api/v1/stays/{propertyId}/blocks` | StayController.block | signed-in | ✅ 2 | ⛔ 2 (400/403) |
| stay | DELETE | `/api/v1/stays/{propertyId}/blocks/{blockId}` | StayController.unblock | signed-in | ✅ 1 | ⛔ 1 (404) |
| stay | POST | `/api/v1/stays/{propertyId}/bookings` | StayController.book | signed-in | ✅ 19 | ✅ 16 |
| stay | GET | `/api/v1/stays/{propertyId}/quote` | StayController.quote | public | ✅ 3 | ✅ 6 |
| stay | PUT | `/api/v1/stays/{propertyId}/settings` | StayController.updateSettings | signed-in | ✅ 11 | ✅ 14 |
| stay | GET | `/api/v1/stays/bookings/{id}` | StayController.get | signed-in | ✅ 3 | ⛔ 2 (404) |
| stay | POST | `/api/v1/stays/bookings/{id}/accept` | StayController.accept | signed-in | ✅ 5 | ✅ 4 |
| stay | POST | `/api/v1/stays/bookings/{id}/cancel` | StayController.cancel | signed-in | ✅ 2 | ⛔ 1 (404) |
| stay | POST | `/api/v1/stays/bookings/{id}/decline` | StayController.decline | signed-in | — | ⛔ 1 (404) |
| stay | POST | `/api/v1/stays/bookings/{id}/mark-paid` | StayController.markPaid | signed-in | ✅ 2 | ✅ 3 |
| stay | POST | `/api/v1/stays/bookings/{id}/pay` | StayController.pay | signed-in | ✅ 3 | ⛔ 1 (400) |
| stay | GET | `/api/v1/stays/bookings/mine` | StayController.mine | signed-in | ✅ 1 | ✅ 15 |
| stay | GET | `/api/v1/stays/hosting` | StayController.hosting | public | — | — |
| stay | GET | `/api/v1/stays/search` | StayController.search | public | ✅ 4 | ✅ 5 |
| subscription | POST | `/api/v1/subscriptions/checkout` | SubscriptionController.checkout | signed-in | ✅ 8 | ⛔ 1 (400) |
| subscription | GET | `/api/v1/subscriptions/me` | SubscriptionController.mine | signed-in | ✅ 1 | ✅ 1 |
| subscription | GET | `/api/v1/subscriptions/plans` | SubscriptionController.plans | public | — | ✅ 7 |
| subscription | PUT | `/api/v1/subscriptions/plans/{plan}` | SubscriptionController.updatePlan | PERM_SUBSCRIPTION_PLANS_MANAGE | — | — |
| subscription | POST | `/api/v1/subscriptions/subscribe` | SubscriptionController.subscribe | signed-in | — | ⛔ 1 (400) |
| timeline | GET | `/api/v1/timeline/mine` | HomeTimelineController.mine | signed-in | — | ✅ 1 |
| transaction | GET | `/api/v1/admin/sale-transactions` | SaleTransactionController.queue | signed-in | — | ⛔ 1 (403) |
| transaction | POST | `/api/v1/admin/sale-transactions/{id}/verify` | SaleTransactionController.verify | signed-in | ✅ 10 | ⛔ 1 (400) |
| transaction | POST | `/api/v1/properties/{id}/sale-transactions` | SaleTransactionController.report | signed-in | ✅ 8 | ⛔ 1 (400) |
| transaction | GET | `/api/v1/properties/{id}/sale-transactions` | SaleTransactionController.forProperty | signed-in | — | ⛔ 1 (404) |
| trust | GET | `/api/v1/orgs/{slug}/trust-ledger` | TrustLedgerController.ledger | signed-in | ✅ 1 | ⛔ 2 (403/404) |
| trust | POST | `/api/v1/orgs/{slug}/trust-ledger` | TrustLedgerController.record | signed-in | ✅ 6 | ⛔ 2 (400/403) |
| user | GET | `/api/v1/admin/staff` | StaffAccessController.list | signed-in | ✅ 1 | ✅ 3 |
| user | PUT | `/api/v1/admin/staff/{userId}` | StaffAccessController.set | signed-in | ✅ 37 | ⛔ 1 (403) |
| user | POST | `/api/v1/users` | UserController.create | PERM_USER_MANAGE | — | — |
| user | GET | `/api/v1/users` | UserController.list | PERM_USER_MANAGE | ✅ 1 | ✅ 4 |
| user | GET | `/api/v1/users/{id}` | UserController.get | signed-in | — | ✅ 5 |
| user | POST | `/api/v1/users/{id}/admin-request` | UserController.requestAdminAccess | signed-in | ✅ 1 | ⛔ 1 (403) |
| user | GET | `/api/v1/users/{id}/landlord-passport` | UserController.landlordPassport | public | ✅ 5 | ✅ 5 |
| user | GET | `/api/v1/users/{id}/landlord-profile` | UserController.landlordProfile | signed-in | — | ⛔ 1 (404) |
| user | PATCH | `/api/v1/users/{id}/profile` | UserController.updateProfile | signed-in | ✅ 2 | ⛔ 1 (403) |
| user | POST | `/api/v1/users/{id}/roles` | UserController.addRole | signed-in | ✅ 2 | ⛔ 1 (403) |
| user | GET | `/api/v1/users/{id}/tenant-passport` | UserController.tenantPassport | signed-in | ✅ 4 | ✅ 13 |
| user | GET | `/api/v1/users/{id}/verification-level` | UserController.verificationLevel | signed-in | — | ✅ 21 |
| user | PATCH | `/api/v1/users/{id}/verify` | UserController.verify | PERM_USER_VERIFY | ✅ 3 | — |
| user | PATCH | `/api/v1/users/{id}/verify-business` | UserController.verifyBusiness | PERM_USER_VERIFY | — | — |
| user | GET | `/api/v1/users/admin-requests` | UserController.adminRequests | PERM_USER_MANAGE | — | — |
| user | PATCH | `/api/v1/users/admin-requests/{id}` | UserController.decideAdminRequest | PERM_USER_MANAGE | — | — |
| user | GET | `/api/v1/users/search` | UserController.search | signed-in | — | ✅ 1 |
| vendor | POST | `/api/v1/service-bookings` | ServiceBookingController.create | signed-in | — | ⛔ 1 (400) |
| vendor | GET | `/api/v1/service-bookings` | ServiceBookingController.list | signed-in | — | ✅ 1 |
| vendor | PATCH | `/api/v1/service-bookings/{id}/cancel` | ServiceBookingController.cancel | signed-in | — | ⛔ 1 (404) |
| vendor | POST | `/api/v1/service-bookings/{id}/feedback` | ServiceBookingController.feedback | signed-in | — | ⛔ 1 (400) |
| vendor | PATCH | `/api/v1/service-bookings/{id}/status` | ServiceBookingController.updateStatus | signed-in | — | ⛔ 1 (400) |
| vendor | GET | `/api/v1/service-bookings/received` | ServiceBookingController.received | signed-in | — | ⛔ 1 (404) |
| vendor | GET | `/api/v1/vendors` | VendorController.list | public | — | ✅ 13 |
| vendor | POST | `/api/v1/vendors` | VendorController.create | PERM_VENDOR_MANAGE | — | — |
| vendor | PATCH | `/api/v1/vendors/{id}/deactivate` | VendorController.deactivate | PERM_VENDOR_MANAGE | — | — |
| vendor | PATCH | `/api/v1/vendors/{id}/verify` | VendorController.verify | PERM_VENDOR_MANAGE | — | — |
| vendor | GET | `/api/v1/vendors/mine` | VendorController.mine | signed-in | — | ⛔ 1 (404) |
| vendor | PATCH | `/api/v1/vendors/mine` | VendorController.updateMine | signed-in | — | ⛔ 1 (404) |
| vendor | POST | `/api/v1/vendors/self` | VendorController.registerSelf | signed-in | — | ⛔ 1 (400) |
| viewing | POST | `/api/v1/viewings` | ViewingController.create | signed-in | — | ⛔ 1 (400) |
| viewing | GET | `/api/v1/viewings` | ViewingController.list | signed-in | — | ✅ 17 |
| viewing | PATCH | `/api/v1/viewings/{id}/cancel` | ViewingController.cancel | signed-in | — | ⛔ 1 (404) |
| viewing | PATCH | `/api/v1/viewings/{id}/check-in` | ViewingController.checkIn | signed-in | — | ⛔ 1 (400) |
| viewing | PATCH | `/api/v1/viewings/{id}/confirm` | ViewingController.confirm | signed-in | — | ⛔ 1 (404) |
| viewing | PATCH | `/api/v1/viewings/{id}/decline` | ViewingController.decline | signed-in | — | ⛔ 1 (404) |
| viewing | POST | `/api/v1/viewings/{id}/feedback` | ViewingController.feedback | signed-in | — | ⛔ 1 (400) |
| wallet | GET | `/api/v1/admin/wallet/withdrawals` | WalletController.staffWithdrawals | signed-in | — | ✅ 3 |
| wallet | PATCH | `/api/v1/admin/wallet/withdrawals/{id}` | WalletController.decide | signed-in | ✅ 5 | ⛔ 1 (403) |
| wallet | GET | `/api/v1/wallet` | WalletController.wallet | signed-in | ✅ 15 | ✅ 17 |
| wallet | POST | `/api/v1/wallet/top-ups` | WalletController.topUp | signed-in | ✅ 8 | ⛔ 1 (400) |
| wallet | POST | `/api/v1/wallet/withdrawals` | WalletController.withdraw | signed-in | ✅ 6 | ⛔ 1 (401) |
| wallet | GET | `/api/v1/wallet/withdrawals` | WalletController.myWithdrawals | signed-in | — | ✅ 3 |
