package net.ripe.rpki.domain.roa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;

public interface RoaEntityService {

    void updateRoasIfNeeded(ManagedCertificateAuthority ca);

}
