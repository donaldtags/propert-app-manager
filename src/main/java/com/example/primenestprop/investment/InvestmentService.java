package com.example.primenestprop.investment;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.market.ZimbabweReitMarketService;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyRepository;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import com.example.primenestprop.user.UserService;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentService {
    private final ReitRepository reits;
    private final InvestmentRepository investments;
    private final UserService users;
    private final PropertyRepository properties;
    private final InvestmentScoreService scoreService;
    private final ZimbabweReitMarketService marketService;

    public InvestmentService(
            ReitRepository reits,
            InvestmentRepository investments,
            UserService users,
            PropertyRepository properties,
            InvestmentScoreService scoreService,
            ZimbabweReitMarketService marketService
    ) {
        this.reits = reits;
        this.investments = investments;
        this.users = users;
        this.properties = properties;
        this.scoreService = scoreService;
        this.marketService = marketService;
    }

    private InvestmentDtos.ReitResponse toResponse(Reit reit) {
        return InvestmentDtos.ReitResponse.from(reit, scoreService, marketService.quoteFor(reit.getTickerSymbol()));
    }

    @Transactional
    public InvestmentDtos.ReitResponse createReit(InvestmentDtos.CreateReitRequest request, AppUser currentUser) {
        if (!currentUser.hasPermission(Permission.INVESTMENT_MANAGE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins or developers can create REITs");
        }
        Reit reit = new Reit();
        reit.setName(request.name());
        reit.setDescription(request.description());
        reit.setMarket(request.market() == null || request.market().isBlank() ? "Zimbabwe" : request.market());
        reit.setUnitPrice(request.unitPrice());
        reit.setProjectedAnnualYield(request.projectedAnnualYield());
        reit.setRiskLevel(request.riskLevel() == null || request.riskLevel().isBlank() ? "MEDIUM" : request.riskLevel());
        reit.setVexEligible(request.vexEligible());
        reit.setTotalUnits(request.totalUnits());
        reit.setUnitsSold(java.math.BigDecimal.ZERO);
        reit.setPropertyType(request.propertyType() == null || request.propertyType().isBlank() ? "RESIDENTIAL" : request.propertyType());
        reit.setCoverImageUrl(request.coverImageUrl());
        reit.setTickerSymbol(request.tickerSymbol());
        if (request.propertyIds() != null && !request.propertyIds().isEmpty()) {
            reit.setProperties(new java.util.LinkedHashSet<>(properties.findAllById(request.propertyIds())));
        }
        return toResponse(reits.save(reit));
    }

    @Transactional
    public InvestmentDtos.ReitResponse updateReitProperties(Long reitId, InvestmentDtos.UpdateReitPropertiesRequest request, AppUser currentUser) {
        if (!currentUser.hasPermission(Permission.INVESTMENT_MANAGE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins or developers can manage a REIT's portfolio");
        }
        Reit reit = reits.findById(reitId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REIT not found"));
        Set<Property> linked = new java.util.LinkedHashSet<>(properties.findAllById(request.propertyIds()));
        reit.setProperties(linked);
        return toResponse(reits.save(reit));
    }

    @Transactional(readOnly = true)
    public List<InvestmentDtos.ReitResponse> activeReits() {
        return reits.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Transactional
    public Investment invest(InvestmentDtos.CreateInvestmentRequest request, AppUser investor) {
        if (!investor.hasPermission(Permission.INVESTMENT_CREATE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only investor or diaspora accounts can invest in a REIT");
        }
        Reit reit = reits.findByIdForUpdate(request.reitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REIT not found"));
        if (!reit.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This REIT is not currently open for investment");
        }
        if (reit.getTotalUnits() != null) {
            java.math.BigDecimal remaining = reit.getAvailableUnits();
            if (request.units().compareTo(remaining) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only " + remaining + " units remain available in this REIT");
            }
            reit.setUnitsSold(reit.getUnitsSold().add(request.units()));
        }
        Investment investment = new Investment();
        investment.setInvestor(investor);
        investment.setReit(reit);
        investment.setUnits(request.units());
        investment.setAmount(reit.getUnitPrice().multiply(request.units()));
        investment.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        investment.setStatus(InvestmentStatus.ACTIVE);
        return investments.save(investment);
    }

    @Transactional
    public List<Investment> sell(InvestmentDtos.SellInvestmentRequest request, AppUser investor) {
        Reit reit = reits.findByIdForUpdate(request.reitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REIT not found"));
        List<Investment> lots = investments.findByInvestorAndReitAndStatusOrderByCreatedAtAsc(
                investor, reit, InvestmentStatus.ACTIVE);

        java.math.BigDecimal owned = lots.stream()
                .map(Investment::getUnits)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (request.units().compareTo(owned) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You only own " + owned + " units in this REIT");
        }

        java.math.BigDecimal remaining = request.units();
        List<Investment> touched = new java.util.ArrayList<>();
        for (Investment lot : lots) {
            if (remaining.signum() <= 0) break;
            java.math.BigDecimal lotUnits = lot.getUnits();
            java.math.BigDecimal sold = lotUnits.min(remaining);
            java.math.BigDecimal newUnits = lotUnits.subtract(sold);
            lot.setAmount(newUnits.signum() == 0
                    ? java.math.BigDecimal.ZERO
                    : lot.getAmount().multiply(newUnits).divide(lotUnits, 2, java.math.RoundingMode.HALF_UP));
            lot.setUnits(newUnits);
            if (newUnits.signum() == 0) {
                lot.setStatus(InvestmentStatus.EXITED);
            }
            remaining = remaining.subtract(sold);
            touched.add(lot);
        }

        if (reit.getTotalUnits() != null) {
            reit.setUnitsSold(reit.getUnitsSold().subtract(request.units()).max(java.math.BigDecimal.ZERO));
        }

        investments.saveAll(touched);
        return investments.findByInvestor(investor);
    }

    @Transactional(readOnly = true)
    public List<Investment> forInvestor(Long investorId, AppUser currentUser) {
        if (!investorId.equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own investments");
        }
        return investments.findByInvestor(users.require(investorId));
    }
}
