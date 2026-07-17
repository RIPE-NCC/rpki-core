package net.ripe.rpki.domain.bgpsec;

import net.ripe.rpki.domain.ManagedCertificateAuthority;

public interface BgpSecEntityService {

    void updateBgpSecIfNeeded(ManagedCertificateAuthority ca);

}