package net.ripe.rpki.ncc.core.domain.support;

import lombok.Getter;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.joda.time.Instant;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PreUpdate;
import javax.persistence.Version;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

@MappedSuperclass
public abstract class EntitySupport implements Entity {

    @Version
    @Column(nullable=false)
    protected Long version;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter
    private Instant updatedAt;

    protected EntitySupport() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void updateUpdatedAt() {
        updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    /**
     * @throws IllegalStateException
     *             the entity validation failed.
     */
    public void assertValid() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<EntitySupport>> result = validator.validate(this);
        if (!result.isEmpty()) {
            throw new IllegalStateException(result.toString());
        }
    }
}
