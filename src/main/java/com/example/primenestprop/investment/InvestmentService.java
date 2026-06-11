package com.example.primenestprop.investment;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentService {
    private final ReitRepository reits;
    private final InvestmentRepository investments;
    private final UserService users;

    public InvestmentService(ReitRepository reits, InvestmentRepository investments, UserService users) {
        this.reits = reits;
        this.investments = investments;
        this.users = users;
    }

    @Transactional
    public Reit createReit(InvestmentDtos.CreateReitRequest request) {
        Reit reit = new Reit();
        reit.setName(request.name());
        reit.setDescription(request.description());
        reit.setMarket(request.market() == null || request.market().isBlank() ? "Zimbabwe" : request.market());
        reit.setUnitPrice(request.unitPrice());
        reit.setProjectedAnnualYield(request.projectedAnnualYield());
        reit.setRiskLevel(request.riskLevel() == null || request.riskLevel().isBlank() ? "MEDIUM" : request.riskLevel());
        reit.setVexEligible(request.vexEligible());
        return reits.save(reit);
    }

    public List<Reit> activeReits() {
        return reits.findByActiveTrue();
    }

    @Transactional
    public Investment invest(InvestmentDtos.CreateInvestmentRequest request) {
        AppUser investor = users.require(request.investorId());
        if (!investor.getRoles().contains(UserRole.INVESTOR) && !investor.getRoles().contains(UserRole.DIASPORA)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "investorId must belong to an investor or diaspora user");
        }
        Reit reit = reits.findById(request.reitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REIT not found"));
        Investment investment = new Investment();
        investment.setInvestor(investor);
        investment.setReit(reit);
        investment.setUnits(request.units());
        investment.setAmount(reit.getUnitPrice().multiply(request.units()));
        investment.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        investment.setStatus(InvestmentStatus.ACTIVE);
        return investments.save(investment);
    }

    public List<Investment> forInvestor(Long investorId) {
        return investments.findByInvestor(users.require(investorId));
    }
}
