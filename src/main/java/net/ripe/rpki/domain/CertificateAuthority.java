package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.ncc.core.domain.support.AggregateRoot;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.util.UUID;

import static java.util.Objects.requireNonNull;


/**
 * A certificate authority is a trusted authority within an PKI responsible for certifying Internet
 * Resources and its Holders.
 *
 * This is the root entity of the {@link HostedCertificateAuthority} aggregate.
 *
 * It is implemented in this system by two types of hosted CAs: the production CA and customer CA,
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

    @ManyToOne(targetEntity = HostedCertificateAuthority.class, fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private ParentCertificateAuthority parent;

    protected CertificateAuthority() {
    }

    public CertificateAuthority(long id, ParentCertificateAuthority parent, X500Principal name) {
        super(id);
        this.uuid = UUID.randomUUID();
        this.parent = parent;
        this.name = requireNonNull(name);
    }

    public X500Principal getName() {
        return name;
    }

    public abstract CertificateAuthorityType getType();

    public abstract CertificateAuthorityData toData();

    @Override
    public ParentCertificateAuthority getParent() {
        return parent;
    }

    public static DateTime calculateValidityNotAfter(DateTime dateTime) {
        return getEndOfYearDateTime(dateTime).plus(GRACEPERIOD);
    }

    private static DateTime getEndOfYearDateTime(DateTime now) {
        return new DateTime(now.getYear() + 1, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
    }

    public boolean isProductionCa() {
        return getType().equals(CertificateAuthorityType.ROOT);
    }

    public boolean isAllResourcesCa() {
        return getType().equals(CertificateAuthorityType.ALL_RESOURCES);
    }
}
