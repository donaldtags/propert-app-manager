package com.example.primenestprop.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaign_tasks", indexes = @Index(name = "idx_campaign_tasks_campaign", columnList = "campaignId"))
@Getter
@Setter
@NoArgsConstructor
public class CampaignTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long campaignId;

    @Column(name = "day_number", nullable = false)
    private int day;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "channel", length = 20, nullable = false)
    private String channelCode;

    @Column(nullable = false, length = 300)
    private String title;

    private Instant doneAt;
    private Long doneByUserId;
    @Column(length = 500) private String note;

    public CampaignChannel getChannel() {
        return CampaignChannel.valueOf(channelCode);
    }

    public void setChannel(CampaignChannel channel) {
        this.channelCode = channel.name();
    }
}
