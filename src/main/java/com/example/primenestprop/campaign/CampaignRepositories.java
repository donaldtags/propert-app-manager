package com.example.primenestprop.campaign;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, Long> {
    Optional<MarketingCampaign> findFirstByPropertyIdAndStoppedFalseOrderByStartDateDesc(Long propertyId);
}

interface CampaignTaskRepository extends JpaRepository<CampaignTask, Long> {
    List<CampaignTask> findByCampaignIdOrderByDayAscIdAsc(Long campaignId);
}
