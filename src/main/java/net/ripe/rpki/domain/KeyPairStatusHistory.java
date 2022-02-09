package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.joda.time.DateTime;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.NotNull;

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
