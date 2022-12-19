package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import net.ripe.rpki.domain.audit.CommandAudit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ToString
public class CommandContext {

    @Getter
    @NonNull
    private final CertificateAuthorityCommand command;

    @Getter
    @NonNull
    private final CommandAudit commandAudit;

    @NonNull
    private final List<@NonNull Object> recordedEvents = new ArrayList<>();

    public CommandContext(@NonNull CertificateAuthorityCommand command, @NonNull CommandAudit commandAudit) {
        this.command = command;
        this.commandAudit = commandAudit;
    }

    public void recordEvent(@NonNull Object event) {
        this.recordedEvents.add(event);
    }

    public @NonNull List<@NonNull Object> getRecordedEvents() {
        return Collections.unmodifiableList(this.recordedEvents);
    }
}
