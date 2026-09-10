package com.example.primenestprop.construction;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ConstructionDtos {
    private ConstructionDtos() {
    }

    public record CreateConstructionProjectRequest(
            @NotBlank String title,
            String country,
            @NotBlank String city,
            String address,
            String contractorName,
            String contractorPhone,
            String contractorCompany,
            BigDecimal budgetTotal,
            String currency,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            Long propertyId
    ) {
    }

    public record UpdateConstructionProjectRequest(
            ConstructionStage stage,
            Integer progressPercent,
            ConstructionStatus status,
            BigDecimal budgetTotal,
            BigDecimal amountPaid,
            String contractorName,
            String contractorPhone,
            String contractorCompany,
            LocalDate startDate,
            LocalDate expectedCompletionDate
    ) {
    }

    public record CreateMilestoneRequest(
            @NotBlank String title,
            String description,
            BigDecimal amountDue,
            LocalDate targetDate
    ) {
    }

    public record PostUpdateRequest(@NotBlank String note) {
    }

    public record ConstructionMilestoneResponse(
            Long id,
            Long projectId,
            String title,
            String description,
            BigDecimal amountDue,
            LocalDate targetDate,
            MilestoneStatus status,
            Instant submittedAt,
            Instant approvedAt,
            Long approvedByUserId,
            Instant createdAt
    ) {
        public static ConstructionMilestoneResponse from(ConstructionMilestone milestone) {
            return new ConstructionMilestoneResponse(
                    milestone.getId(),
                    milestone.getProject().getId(),
                    milestone.getTitle(),
                    milestone.getDescription(),
                    milestone.getAmountDue(),
                    milestone.getTargetDate(),
                    milestone.getStatus(),
                    milestone.getSubmittedAt(),
                    milestone.getApprovedAt(),
                    milestone.getApprovedBy() == null ? null : milestone.getApprovedBy().getId(),
                    milestone.getCreatedAt()
            );
        }
    }

    public record ConstructionUpdateResponse(
            Long id,
            Long projectId,
            String note,
            Long postedByUserId,
            String postedByName,
            Instant createdAt
    ) {
        public static ConstructionUpdateResponse from(ConstructionUpdate update) {
            return new ConstructionUpdateResponse(
                    update.getId(),
                    update.getProject().getId(),
                    update.getNote(),
                    update.getPostedBy() == null ? null : update.getPostedBy().getId(),
                    update.getPostedBy() == null ? null : update.getPostedBy().getFullName(),
                    update.getCreatedAt()
            );
        }
    }

    public record ConstructionProjectResponse(
            Long id,
            Long ownerId,
            Long propertyId,
            String title,
            String country,
            String city,
            String address,
            String contractorName,
            String contractorPhone,
            String contractorCompany,
            ConstructionStage stage,
            int progressPercent,
            ConstructionStatus status,
            BigDecimal budgetTotal,
            BigDecimal amountPaid,
            BigDecimal amountRemaining,
            String currency,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            Instant createdAt,
            Instant updatedAt,
            String latestUpdateNote,
            Instant latestUpdateAt
    ) {
        public static ConstructionProjectResponse from(ConstructionProject project, ConstructionUpdate latestUpdate) {
            BigDecimal budget = project.getBudgetTotal() == null ? BigDecimal.ZERO : project.getBudgetTotal();
            BigDecimal paid = project.getAmountPaid() == null ? BigDecimal.ZERO : project.getAmountPaid();
            return new ConstructionProjectResponse(
                    project.getId(),
                    project.getOwner().getId(),
                    project.getProperty() == null ? null : project.getProperty().getId(),
                    project.getTitle(),
                    project.getCountry(),
                    project.getCity(),
                    project.getAddress(),
                    project.getContractorName(),
                    project.getContractorPhone(),
                    project.getContractorCompany(),
                    project.getStage(),
                    project.getProgressPercent(),
                    project.getStatus(),
                    project.getBudgetTotal(),
                    project.getAmountPaid(),
                    budget.subtract(paid),
                    project.getCurrency(),
                    project.getStartDate(),
                    project.getExpectedCompletionDate(),
                    project.getCreatedAt(),
                    project.getUpdatedAt(),
                    latestUpdate == null ? null : latestUpdate.getNote(),
                    latestUpdate == null ? null : latestUpdate.getCreatedAt()
            );
        }
    }
}
