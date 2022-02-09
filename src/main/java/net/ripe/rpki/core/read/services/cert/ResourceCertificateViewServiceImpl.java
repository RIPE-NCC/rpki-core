package net.ripe.rpki.core.read.services.cert;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
@Transactional(readOnly = true)
public class ResourceCertificateViewServiceImpl implements ResourceCertificateViewService {

    @Resource
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Override
    public IpResourceSet findCertifiedResources(Long caId) {
        HostedCertificateAuthority hostedCertificateAuthority = certificateAuthorityRepository.findHostedCa(caId);
        if (hostedCertificateAuthority != null) {
            return hostedCertificateAuthority.getCertifiedResources();
        } else {
            return null;
        }
    }

}
