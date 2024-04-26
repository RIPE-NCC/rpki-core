package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class KeyPairStatusHistory {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter
    private KeyPairStatus status;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    @Getter
    private DateTime changedAt;

    public KeyPairStatusHistory() {
    }

    public KeyPairStatusHistory(KeyPairStatus status, DateTime changedAt) {
        super();
        this.status = status;
        this.changedAt = changedAt;
    }
}
