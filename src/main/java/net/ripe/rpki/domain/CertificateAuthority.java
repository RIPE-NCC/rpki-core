package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.ncc.core.domain.support.AggregateRoot;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import javax.security.auth.x500.X500Principal;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.UUID;

/**
 * A certificate authority is a trusted authority within an PKI responsible for certifying Internet
 * Resources and its Holders.
 *
 * This is the root entity of the {@link CertificateAuthority} aggregate.
 */
@Entity
@Table(name = "certificateauthority")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType = DiscriminatorType.STRING)
public abstract class CertificateAuthority extends AggregateRoot implements ChildCertificateAuthority {

    public static final Period GRACEPERIOD = Period.months(6);

    @NotNull
    @Column(unique = true)
    @Getter
    private UUID uuid;

    @NotNull
    @Column(nullable = false, unique = true)
    private X500Principal name;

    @Getter
    @ManyToOne(targetEntity = ManagedCertificateAuthority.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ParentCertificateAuthority parent;

    protected CertificateAuthority() {
    }

    protected CertificateAuthority(long id, @NonNull X500Principal name, @NonNull UUID uuid, ParentCertificateAuthority parent) {
        super(id);
        this.name = name;
        this.uuid = uuid;
        this.parent = parent;
    }

    public X500Principal getName() {
        return name;
    }

    public abstract CertificateAuthorityType getType();

    public abstract CertificateAuthorityData toData();

    public static DateTime calculateValidityNotAfter(DateTime dateTime) {
        return getEndOfYearDateTime(dateTime).plus(GRACEPERIOD);
    }

    private static DateTime getEndOfYearDateTime(DateTime now) {
        return new DateTime(now.getYear() + 1, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
    }

    public boolean isProductionCa() {
        return getType() == CertificateAuthorityType.ROOT;
    }

    public boolean isIntermediateCa() {
        return getType() == CertificateAuthorityType.INTERMEDIATE;
    }

    public boolean isAllResourcesCa() {
        return getType() == CertificateAuthorityType.ALL_RESOURCES;
    }

    public abstract Optional<ManagedCertificateAuthority> asManagedCertificateAuthority();

    @Override
    public void switchParentTo(ParentCertificateAuthority newParent) {
        this.parent = newParent;
    }

    /**
     * Calculates the depth of this CA in the CA hierarchy (in other words, it is the length of the parent
     * chain).
     * @return the depth of this CA in the CA hierarchy. The ALL_RESOURCES CA is at depth 0, ROOT at 1, etc.
     */
    public int depth() {
        int depth = 0;
        for (ParentCertificateAuthority parent = this.parent; parent != null; parent = parent.getParent()) {
            depth++;
        }
        return depth;
    }
}
