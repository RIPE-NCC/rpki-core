package net.ripe.rpki.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "provisioning_stat")
@SequenceGenerator(name = "seq_provisioning_stat", sequenceName = "seq_all", allocationSize = 1)
@AllArgsConstructor
public class ProvisioningStatRecord extends EntitySupport {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_provisioning_stat")
    @Getter
    private Long id;

    @Column(name = "non_hosted_ca_uuid", nullable = false)
    @Getter
    private UUID nonHostedCaUUID;

    @Column(name = "last_success")
    @Nullable
    private Instant lastSuccess;

    @Column(name = "last_failure")
    @Nullable
    private Instant lastFailure;

    @Column(name = "last_failure_reason")
    @Nullable
    private String lastFailureReason;

    ProvisioningStatRecord() {}

    public ProvisioningStatRecord(UUID nonHostedCaUUID) {
        this.nonHostedCaUUID = nonHostedCaUUID;
    }

    public Optional<Instant> getLastSuccess() {
        return Optional.ofNullable(this.lastSuccess);
    }

    public void setLastSuccess(@NonNull Instant time) {
        this.lastSuccess = time;
    }

    public Optional<Pair<Instant, String>> getLastFailure() {
        return Optional.of(Pair.of(lastFailure, lastFailureReason)).filter(x -> x.getLeft() != null && x.getRight() != null);
    }

    public void setLastFailure(@NonNull Instant time, @NonNull String reason) {
        this.lastFailure = time;
        this.lastFailureReason = reason;
    }
}
