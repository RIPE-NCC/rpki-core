package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.bgpsec.Csr;
import net.ripe.rpki.domain.bgpsec.RouterId;

@Getter
public class CreateBgpSecConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final Asn asn;
    private final RouterId routerId;
    private final String csr;
    private final String keyIdentifier;

    public CreateBgpSecConfigurationCommand(VersionedId certificateAuthorityId,
                                            Asn asn, RouterId routerId, String csr) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.asn = asn;
        this.routerId = routerId;
        this.csr = csr;
        this.keyIdentifier = Csr.getKeyIdentifier(csr);
    }

    @Override
    public String getCommandSummary() {
        return "Added BGPSec configuration: ASN: %s, Router-ID: %d, Key-Identifier: %s".formatted(
            asn.toString(), routerId.value(), keyIdentifier
        );
    }
}
