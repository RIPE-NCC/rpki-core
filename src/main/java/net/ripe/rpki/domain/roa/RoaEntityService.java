package net.ripe.rpki.domain.roa;

import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.domain.HostedCertificateAuthority;

import java.util.Collection;


public interface RoaEntityService extends CertificateAuthorityEventVisitor {

    void updateRoasIfNeeded(HostedCertificateAuthority ca);

    void logRoaPrefixDeletion(RoaConfiguration configuration, Collection<? extends RoaConfigurationPrefix> deletedPrefixes);
}
