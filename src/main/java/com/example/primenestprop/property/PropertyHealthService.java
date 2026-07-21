package com.example.primenestprop.property;

import com.example.primenestprop.escrow.EscrowRepository;
import com.example.primenestprop.escrow.EscrowStatus;
import com.example.primenestprop.maintenance.MaintenanceRepository;
import com.example.primenestprop.maintenance.MaintenanceRequest;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.neighbourhood.NeighbourhoodProfile;
import com.example.primenestprop.neighbourhood.NeighbourhoodProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PropertyHealthService {
    private final MaintenanceRepository maintenance;
    private final EscrowRepository escrows;
    private final NeighbourhoodProfileRepository neighbourhoods;

    public PropertyHealthService(
            MaintenanceRepository maintenance,
            EscrowRepository escrows,
            NeighbourhoodProfileRepository neighbourhoods
    ) {
        this.maintenance = maintenance;
        this.escrows = escrows;
        this.neighbourhoods = neighbourhoods;
    }

    public PropertyHealthScore compute(Property property) {
        int verification = switch (property.getVerificationStatus()) {
            case VERIFIED -> 100;
            case PENDING -> 60;
            case UNVERIFIED -> 40;
            case REJECTED -> 0;
        };

        int maintenanceScore = maintenanceScore(property);
        int transactionSafety = transactionSafetyScore(property);
        Integer solar = property.isSolarInstalled() ? 100 : 0;
        Integer water = waterScore(property.getWaterSource());
        int occupancy = occupancyScore(property.getStatus());
        Integer neighbourhood = neighbourhoodScore(property);

        List<Integer> countedSubScores = new java.util.ArrayList<>(
                List.of(verification, maintenanceScore, transactionSafety, occupancy));
        if (water != null) countedSubScores.add(water);
        // solar is always known (declared true/false), so always counted
        countedSubScores.add(solar);
        if (neighbourhood != null) countedSubScores.add(neighbourhood);

        int overall = (int) Math.round(countedSubScores.stream().mapToInt(Integer::intValue).average().orElse(0));

        return new PropertyHealthScore(overall, verification, maintenanceScore, transactionSafety, solar, water, occupancy, neighbourhood);
    }

    private int maintenanceScore(Property property) {
        List<MaintenanceRequest> requests = maintenance.findByProperty(property);
        if (requests.isEmpty()) {
            return 100;
        }
        long resolvedOrCancelled = requests.stream()
                .filter(r -> r.getStatus() == MaintenanceStatus.RESOLVED || r.getStatus() == MaintenanceStatus.CANCELLED)
                .count();
        return (int) Math.round((resolvedOrCancelled * 100.0) / requests.size());
    }

    private int transactionSafetyScore(Property property) {
        int score = property.isEscrowRequired() ? 60 : 30;
        boolean hasDispute = escrows.findByPropertyOrderByCreatedAtDesc(property).stream()
                .anyMatch(e -> e.getStatus() == EscrowStatus.DISPUTED);
        if (hasDispute) {
            score -= 30;
        }
        if (property.getVerificationStatus() == VerificationStatus.VERIFIED) {
            score += 40;
        }
        return Math.max(0, Math.min(100, score));
    }

    private Integer waterScore(WaterSource waterSource) {
        if (waterSource == null) {
            return null;
        }
        return switch (waterSource) {
            case MUNICIPAL -> 100;
            case BOREHOLE -> 90;
            case WELL -> 70;
            case TANKER -> 50;
            case OTHER -> 60;
        };
    }

    /**
     * Null when no admin has curated a NeighbourhoodProfile for this suburb yet - never a
     * fabricated placeholder. Once curated, the score reflects how complete that profile is
     * (schools/hospitals/transport/shopping/general notes filled in).
     */
    private Integer neighbourhoodScore(Property property) {
        return neighbourhoods.findByCityIgnoreCaseAndSuburbIgnoreCase(property.getCity(), property.getSuburb())
                .map(this::completenessScore)
                .orElse(null);
    }

    private int completenessScore(NeighbourhoodProfile profile) {
        int filled = 0;
        int total = 5;
        if (hasText(profile.getSchoolsNote())) filled++;
        if (hasText(profile.getHospitalsNote())) filled++;
        if (hasText(profile.getTransportNote())) filled++;
        if (hasText(profile.getShoppingNote())) filled++;
        if (hasText(profile.getGeneralNote())) filled++;
        return (int) Math.round((filled * 100.0) / total);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int occupancyScore(PropertyStatus status) {
        return switch (status) {
            case OCCUPIED, SOLD -> 100;
            case RESERVED -> 80;
            case AVAILABLE -> 50;
            case INACTIVE -> 20;
            case DRAFT -> 0;
        };
    }
}
