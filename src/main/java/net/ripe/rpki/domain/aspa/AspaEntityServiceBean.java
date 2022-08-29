package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service("aspaEntityServiceBean")
@ConditionalOnProperty(prefix = "aspa.feature", value = "enabled", havingValue = "true")
public class AspaEntityServiceBean implements AspaEntityService {
    @Override
    public void aspaConfigurationUpdated(ManagedCertificateAuthority ca) {
        throw new UnsupportedOperationException("AspaEntityServiceBean has not been implemented yet.");
    }
}
