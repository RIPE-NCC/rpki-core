package net.ripe.rpki.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * JPA mappable version of a ValidityPeriod
 */
@Embeddable
@EqualsAndHashCode
@Getter
public class EmbeddedValidityPeriod {

    @Column(name = "validity_not_before", nullable = true)
    private DateTime notValidBefore;

    @Column(name = "validity_not_after", nullable = true)
    private DateTime notValidAfter;

    protected EmbeddedValidityPeriod() {
    }

    public EmbeddedValidityPeriod(@NonNull ValidityPeriod period) {
        this.notValidBefore = period.getNotValidBefore();
        this.notValidAfter = period.getNotValidAfter();
    }
    
    public ValidityPeriod toValidityPeriod() {
        return new ValidityPeriod(getNotValidBefore(), getNotValidAfter());
    }
    
    @Override
    public String toString() {
        return getNotValidBefore() + " - " + getNotValidAfter();
    }

}
