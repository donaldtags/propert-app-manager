package com.example.estatex.campaign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CampaignDtos {
    private CampaignDtos() {
    }

    public record SetTaskRequest(@NotNull Boolean done, @Size(max = 500) String note) {
    }

    public record TaskResponse(Long id, int day, LocalDate dueOn, CampaignChannel channel, String title, boolean done,
                               Instant doneAt, boolean overdue, String note) {
    }

    public record CampaignResponse(Long id, Long propertyId, LocalDate startDate, LocalDate endDate, int today,
                                   boolean stopped, int tasksDone, int tasksTotal, List<TaskResponse> tasks,
                                   Map<String, Long> enquiriesByChannel, long enquiriesTotal, long viewingsTotal) {
    }
}
