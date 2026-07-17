package net.ripe.rpki.domain.bgpsec;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.ripencc.support.persistence.Repository;

import java.util.List;

public interface BgpSecEntityRepository extends Repository<BgpSecEntity> {
    List<BgpSecEntity> findCurrentByCertificateAuthority(ManagedCertificateAuthority ca);

    int deleteByCertificateSigningKeyPair(KeyPairEntity keyPair);
}