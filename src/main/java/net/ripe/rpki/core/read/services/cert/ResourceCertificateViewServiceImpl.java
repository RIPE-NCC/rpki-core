package net.ripe.rpki.core.read.services.cert;

import lombok.NonNull;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.security.PublicKey;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class ResourceCertificateViewServiceImpl implements ResourceCertificateViewService {

    @Resource
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public IpResourceSet findCertifiedResources(Long caId) {
        HostedCertificateAuthority hostedCertificateAuthority = certificateAuthorityRepository.findHostedCa(caId);
        if (hostedCertificateAuthority != null) {
            return hostedCertificateAuthority.getCertifiedResources();
        } else {
            return null;
        }
    }

    @Override
    public Optional<ResourceCertificateData> findCurrentIncomingResourceCertificate(long caId) {
        try {
            return Optional.of(
                entityManager.createQuery(
                        "SELECT rc" +
                            "      FROM HostedCertificateAuthority ca" +
                            "      JOIN ca.keyPairs kp" +
                            "      JOIN kp.incomingResourceCertificate rc " +
                            " WHERE ca.id = :caId " +
                            "   AND kp.status = :current",
                        IncomingResourceCertificate.class
                    )
                    .setParameter("caId", caId)
                    .setParameter("current", KeyPairStatus.CURRENT)
                    .getSingleResult()
                    .toData()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResourceCertificateData> findCurrentOutgoingResourceCertificate(long requestingCaId, @NonNull PublicKey subjectPublicKey) {
        try {
            return Optional.of(
                entityManager.createQuery(
                        "  FROM OutgoingResourceCertificate rc " +
                            " WHERE rc.requestingCertificateAuthority.id = :requestingCaId " +
                            "   AND rc.encodedSubjectPublicKey = :encodedSubjectPublicKey" +
                            "   AND rc.status = :current" +
                            "   AND rc.embedded = false",
                        OutgoingResourceCertificate.class
                    )
                    .setParameter("requestingCaId", requestingCaId)
                    .setParameter("encodedSubjectPublicKey", subjectPublicKey.getEncoded())
                    .setParameter("current", OutgoingResourceCertificateStatus.CURRENT)
                    .getSingleResult()
                    .toData()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
