package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;

@Getter
public class BgpSecCertificateIssuanceCommand extends ChildSharedParentCertificateAuthorityCommand {

    @NonNull
    private final Asn asn;

    @NonNull
    private final String csr;

    @NonNull
    private final String keyIdentifier;

    public BgpSecCertificateIssuanceCommand(VersionedId certificateAuthorityId,
                                            @NonNull Asn asn,
                                            @NonNull String csr,
                                            @NonNull String keyIdentifier) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.asn = asn;
        this.csr = csr;
        this.keyIdentifier = keyIdentifier;
    }

    @Override
    public String getCommandSummary() {
        return "Process a BGPSec certificate issuance request.";
    }
}
