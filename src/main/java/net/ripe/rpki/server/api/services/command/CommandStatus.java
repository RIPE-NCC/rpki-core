package net.ripe.rpki.server.api.services.command;

import lombok.Data;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@Data
public class CommandStatus {

    private TransactionStatus transactionStatus;
    private boolean hasEffect = true;

    public CommandStatus() {
    }

    public CommandStatus(final TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public static CommandStatus create() {
        return new CommandStatus(new SimpleTransactionStatus());
    }
}
