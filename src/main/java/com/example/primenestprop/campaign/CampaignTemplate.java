package com.example.primenestprop.campaign;

import java.util.List;

/** The standard 21-day launch plan. Each day's tasks are created when a campaign starts. */
final class CampaignTemplate {
    record Task(int day, CampaignChannel channel, String title) {
    }

    static final List<Task> TASKS = List.of(
            new Task(1, CampaignChannel.PORTAL, "Listing live on EstateX and property portals with professional photos"),
            new Task(1, CampaignChannel.WHATSAPP, "WhatsApp broadcast to matching buyers in the database"),
            new Task(1, CampaignChannel.INSTAGRAM, "Instagram launch post (square image + carousel)"),
            new Task(2, CampaignChannel.FACEBOOK, "Facebook Marketplace and property groups"),
            new Task(2, CampaignChannel.NETWORK, "Call 10 qualified buyers whose budget and area match"),
            new Task(3, CampaignChannel.INSTAGRAM, "Instagram stories and a short walk-through reel"),
            new Task(3, CampaignChannel.SIGNBOARD, "Signboard up at the property"),
            new Task(4, CampaignChannel.EMAIL, "Email to diaspora and investor list"),
            new Task(5, CampaignChannel.WHATSAPP, "WhatsApp status and agent groups"),
            new Task(6, CampaignChannel.OPEN_HOUSE, "First open house / block viewing"),
            new Task(7, CampaignChannel.REPORTING, "Week 1 seller report sent to the owner"),
            new Task(8, CampaignChannel.INSTAGRAM, "Feature post: best room or selling point"),
            new Task(9, CampaignChannel.NETWORK, "Follow up every enquiry and viewing from week 1"),
            new Task(10, CampaignChannel.NETWORK, "Brief referral partners (other agents, banks, lawyers)"),
            new Task(11, CampaignChannel.FACEBOOK, "Refresh / boost the Facebook posts"),
            new Task(12, CampaignChannel.PORTAL, "Refresh portal listings so they surface again"),
            new Task(13, CampaignChannel.OPEN_HOUSE, "Second open house"),
            new Task(14, CampaignChannel.REPORTING, "Week 2 seller report and price review with the owner"),
            new Task(15, CampaignChannel.INSTAGRAM, "Neighbourhood reel: schools, shops, transport"),
            new Task(16, CampaignChannel.WHATSAPP, "Second WhatsApp broadcast with a new angle"),
            new Task(17, CampaignChannel.NETWORK, "Call back warm buyers and earlier viewers"),
            new Task(18, CampaignChannel.EMAIL, "Email update to the buyer list"),
            new Task(19, CampaignChannel.FACEBOOK, "Re-post in Facebook groups"),
            new Task(20, CampaignChannel.OPEN_HOUSE, "Final viewing push"),
            new Task(21, CampaignChannel.REPORTING, "Week 3 seller report with next-step recommendation"));

    private CampaignTemplate() {
    }
}
