package com.example.primenestprop.construction;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import com.example.primenestprop.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConstructionService {
    private final ConstructionProjectRepository projects;
    private final ConstructionMilestoneRepository milestones;
    private final ConstructionUpdateRepository updates;
    private final PropertyService properties;
    private final UserService users;

    public ConstructionService(
            ConstructionProjectRepository projects,
            ConstructionMilestoneRepository milestones,
            ConstructionUpdateRepository updates,
            PropertyService properties,
            UserService users
    ) {
        this.projects = projects;
        this.milestones = milestones;
        this.updates = updates;
        this.properties = properties;
        this.users = users;
    }

    @Transactional
    public ConstructionProject create(ConstructionDtos.CreateConstructionProjectRequest request, AppUser owner) {
        ConstructionProject project = new ConstructionProject();
        project.setOwner(owner);
        if (request.propertyId() != null) {
            project.setProperty(properties.require(request.propertyId()));
        }
        project.setTitle(request.title());
        if (request.country() != null) project.setCountry(request.country());
        project.setCity(request.city());
        project.setAddress(request.address());
        project.setContractorName(request.contractorName());
        project.setContractorPhone(request.contractorPhone());
        project.setContractorCompany(request.contractorCompany());
        project.setBudgetTotal(request.budgetTotal());
        if (request.currency() != null) project.setCurrency(request.currency());
        project.setStartDate(request.startDate());
        project.setExpectedCompletionDate(request.expectedCompletionDate());
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public ConstructionProject require(Long id) {
        return projects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Construction project not found"));
    }

    @Transactional(readOnly = true)
    public ConstructionProject get(Long id, AppUser currentUser) {
        ConstructionProject project = require(id);
        assertOwnerOrAdmin(project, currentUser);
        return project;
    }

    @Transactional(readOnly = true)
    public List<ConstructionProject> listForOwner(Long ownerId, AppUser currentUser) {
        requireSelfOrAdmin(ownerId, currentUser);
        AppUser owner = users.require(ownerId);
        return projects.findByOwnerOrderByCreatedAtDesc(owner);
    }

    /** No auth check — for callers (e.g. the diaspora dashboard) that already authorized the whole request. */
    @Transactional(readOnly = true)
    public List<ConstructionProject> forOwner(AppUser owner) {
        return projects.findByOwnerOrderByCreatedAtDesc(owner);
    }

    @Transactional
    public ConstructionProject update(Long id, ConstructionDtos.UpdateConstructionProjectRequest request, AppUser currentUser) {
        ConstructionProject project = require(id);
        assertOwnerOrAdmin(project, currentUser);
        if (request.stage() != null) project.setStage(request.stage());
        if (request.progressPercent() != null) {
            project.setProgressPercent(Math.max(0, Math.min(100, request.progressPercent())));
        }
        if (request.status() != null) project.setStatus(request.status());
        if (request.budgetTotal() != null) project.setBudgetTotal(request.budgetTotal());
        if (request.amountPaid() != null) project.setAmountPaid(request.amountPaid());
        if (request.contractorName() != null) project.setContractorName(request.contractorName());
        if (request.contractorPhone() != null) project.setContractorPhone(request.contractorPhone());
        if (request.contractorCompany() != null) project.setContractorCompany(request.contractorCompany());
        if (request.startDate() != null) project.setStartDate(request.startDate());
        if (request.expectedCompletionDate() != null) project.setExpectedCompletionDate(request.expectedCompletionDate());
        return project;
    }

    @Transactional
    public ConstructionMilestone addMilestone(Long projectId, ConstructionDtos.CreateMilestoneRequest request, AppUser currentUser) {
        ConstructionProject project = require(projectId);
        assertOwnerOrAdmin(project, currentUser);
        ConstructionMilestone milestone = new ConstructionMilestone();
        milestone.setProject(project);
        milestone.setTitle(request.title());
        milestone.setDescription(request.description());
        milestone.setAmountDue(request.amountDue());
        milestone.setTargetDate(request.targetDate());
        return milestones.save(milestone);
    }

    @Transactional(readOnly = true)
    public List<ConstructionMilestone> milestonesFor(Long projectId, AppUser currentUser) {
        ConstructionProject project = require(projectId);
        assertOwnerOrAdmin(project, currentUser);
        return milestones.findByProjectOrderByTargetDateAscCreatedAtAsc(project);
    }

    @Transactional
    public ConstructionMilestone submitMilestone(Long milestoneId, AppUser currentUser) {
        ConstructionMilestone milestone = requireMilestone(milestoneId);
        assertOwnerOrAdmin(milestone.getProject(), currentUser);
        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only pending milestones can be submitted for approval");
        }
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        milestone.setSubmittedAt(Instant.now());
        return milestone;
    }

    /** The diaspora owner (or an admin) approving is the gate before payment release. */
    @Transactional
    public ConstructionMilestone approveMilestone(Long milestoneId, AppUser currentUser) {
        ConstructionMilestone milestone = requireMilestone(milestoneId);
        assertOwnerOrAdmin(milestone.getProject(), currentUser);
        if (milestone.getStatus() != MilestoneStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only submitted milestones can be approved");
        }
        milestone.setStatus(MilestoneStatus.APPROVED);
        milestone.setApprovedAt(Instant.now());
        milestone.setApprovedBy(currentUser);
        return milestone;
    }

    @Transactional
    public ConstructionMilestone markMilestonePaid(Long milestoneId, AppUser currentUser) {
        ConstructionMilestone milestone = requireMilestone(milestoneId);
        ConstructionProject project = milestone.getProject();
        assertOwnerOrAdmin(project, currentUser);
        if (milestone.getStatus() != MilestoneStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only approved milestones can be marked paid");
        }
        milestone.setStatus(MilestoneStatus.PAID);
        if (milestone.getAmountDue() != null) {
            BigDecimal paidSoFar = project.getAmountPaid() == null ? BigDecimal.ZERO : project.getAmountPaid();
            project.setAmountPaid(paidSoFar.add(milestone.getAmountDue()));
        }
        return milestone;
    }

    @Transactional
    public ConstructionUpdate postUpdate(Long projectId, ConstructionDtos.PostUpdateRequest request, AppUser currentUser) {
        ConstructionProject project = require(projectId);
        assertOwnerOrAdmin(project, currentUser);
        ConstructionUpdate update = new ConstructionUpdate();
        update.setProject(project);
        update.setNote(request.note());
        update.setPostedBy(currentUser);
        return updates.save(update);
    }

    @Transactional(readOnly = true)
    public List<ConstructionUpdate> updatesFor(Long projectId, AppUser currentUser) {
        ConstructionProject project = require(projectId);
        assertOwnerOrAdmin(project, currentUser);
        return updates.findByProjectOrderByCreatedAtDesc(project);
    }

    @Transactional(readOnly = true)
    public ConstructionUpdate latestUpdateFor(ConstructionProject project) {
        return updates.findFirstByProjectOrderByCreatedAtDesc(project).orElse(null);
    }

    private ConstructionMilestone requireMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    private void assertOwnerOrAdmin(ConstructionProject project, AppUser currentUser) {
        if (!project.getOwner().getId().equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the project owner or an admin can access this construction project");
        }
    }

    private void requireSelfOrAdmin(Long id, AppUser currentUser) {
        if (!currentUser.getId().equals(id) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own construction projects");
        }
    }
}
