package org.example.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    private BigDecimal basePrice = new BigDecimal("2000.00");
    private BigDecimal trialPrice = new BigDecimal("1000.00");
    private BigDecimal multiSubjectPrice = new BigDecimal("1800.00");
    private BigDecimal referralPrice = new BigDecimal("1800.00");

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getTrialPrice() {
        return trialPrice;
    }

    public void setTrialPrice(BigDecimal trialPrice) {
        this.trialPrice = trialPrice;
    }

    public BigDecimal getMultiSubjectPrice() {
        return multiSubjectPrice;
    }

    public void setMultiSubjectPrice(BigDecimal multiSubjectPrice) {
        this.multiSubjectPrice = multiSubjectPrice;
    }

    public BigDecimal getReferralPrice() {
        return referralPrice;
    }

    public void setReferralPrice(BigDecimal referralPrice) {
        this.referralPrice = referralPrice;
    }
}
