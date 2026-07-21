package com.example.primenestprop.ai;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * "Can I afford this property?" - a deterministic affordability check, not an LLM call. It
 * applies two widely-used, explainable rules rather than a black-box prediction:
 *  1. The 30% rent-to-income guideline (rent should not exceed ~30% of gross monthly income).
 *  2. A 40% total-debt-to-income ceiling (existing debt + this rent should not exceed ~40% of
 *     gross monthly income).
 * The lower of the two is the recommended maximum rent. Deterministic and explainable beats a
 * fabricated "AI prediction" here, since a wrong affordability call has real financial consequences
 * for a tenant.
 */
@Service
public class AffordabilityService {
    private static final BigDecimal RENT_TO_INCOME_RATIO = new BigDecimal("0.30");
    private static final BigDecimal DEBT_TO_INCOME_CEILING = new BigDecimal("0.40");

    private final PropertyService properties;

    public AffordabilityService(PropertyService properties) {
        this.properties = properties;
    }

    public AiDtos.AffordabilityResponse evaluate(AiDtos.AffordabilityRequest request) {
        BigDecimal income = request.grossMonthlyIncome();
        BigDecimal existingDebt = request.existingMonthlyDebt() == null ? BigDecimal.ZERO : request.existingMonthlyDebt();

        BigDecimal maxByRentRule = income.multiply(RENT_TO_INCOME_RATIO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxByDti = income.multiply(DEBT_TO_INCOME_CEILING).subtract(existingDebt).setScale(2, RoundingMode.HALF_UP);
        if (maxByDti.signum() < 0) {
            maxByDti = BigDecimal.ZERO;
        }
        BigDecimal recommendedMaxRent = maxByRentRule.min(maxByDti);

        Boolean fitsRecommendedBudget = null;
        BigDecimal propertyRent = null;
        String note = "Based on the standard guideline that rent should not exceed 30% of gross monthly income, "
                + "and total debt (including rent) should not exceed 40%.";

        if (request.propertyId() != null) {
            Property property = properties.require(request.propertyId());
            if (property.getListingType() == ListingType.SALE) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "This affordability check is for rentals. Use a mortgage affordability calculator for sale listings.");
            }
            propertyRent = property.getPrice();
            fitsRecommendedBudget = propertyRent.compareTo(recommendedMaxRent) <= 0;
            note += fitsRecommendedBudget
                    ? " This property's rent is within your recommended budget."
                    : " This property's rent exceeds your recommended budget.";
        }

        return new AiDtos.AffordabilityResponse(
                maxByRentRule,
                maxByDti,
                recommendedMaxRent,
                propertyRent,
                fitsRecommendedBudget,
                note
        );
    }
}
