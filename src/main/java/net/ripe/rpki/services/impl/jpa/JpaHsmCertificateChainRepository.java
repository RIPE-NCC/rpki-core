package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.hsm.HsmCertificateChain;
import net.ripe.rpki.domain.hsm.HsmCertificateChainRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaHsmCertificateChainRepository extends JpaRepository<HsmCertificateChain> implements HsmCertificateChainRepository {
    @Override
    protected Class<HsmCertificateChain> getEntityClass() {
        return HsmCertificateChain.class;
    }
}
