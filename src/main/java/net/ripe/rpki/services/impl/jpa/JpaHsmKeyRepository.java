package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.hsm.HsmKey;
import net.ripe.rpki.domain.hsm.HsmKeyRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaHsmKeyRepository extends JpaRepository<HsmKey> implements HsmKeyRepository {
    @Override
    protected Class<HsmKey> getEntityClass() {
        return HsmKey.class;
    }
}
