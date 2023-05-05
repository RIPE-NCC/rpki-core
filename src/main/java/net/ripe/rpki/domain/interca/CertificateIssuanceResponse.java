package net.ripe.rpki.domain.interca;

import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import org.apache.commons.lang3.Validate;

import java.net.URI;
import java.util.EnumSet;

/**
 * This class does not represent any RFC and used for internal request-response modelling
 * of hosted CAs parent-child interaction and represents the response to CertificateIssuanceRequest.
 */
@Value
public class CertificateIssuanceResponse {

    @NonNull ImmutableResourceSet inheritedResources;
    @NonNull X509ResourceCertificate certificate;
    @NonNull URI publicationUri;

    public CertificateIssuanceResponse(@NonNull X509ResourceCertificate certificate, @NonNull URI publicationUri) {
        Validate.isTrue(!certificate.isResourceSetInherited(), "cannot determine certified resources when certificate has INHERITED resources");
        this.inheritedResources = ImmutableResourceSet.empty();
        this.certificate = certificate;
        this.publicationUri = publicationUri;
        invariant();
    }

    public CertificateIssuanceResponse(@NonNull ImmutableResourceSet inheritedResources, @NonNull X509ResourceCertificate certificate, @NonNull URI publicationUri) {
        this.inheritedResources = inheritedResources;
        this.certificate = certificate;
        this.publicationUri = publicationUri;
        invariant();
    }

    public static CertificateIssuanceResponse fromTaSigningResponse(SigningResponse response) {
        return new CertificateIssuanceResponse(response.getCertificate(), response.getPublicationUri());
    }

    private void invariant() {
        Validate.isTrue(!inheritedResources.intersects(certificate.resources()), "inherited and certificate resources cannot overlap");
        EnumSet<IpResourceType> inheritedResourceTypes = certificate.getInheritedResourceTypes();
        for (IpResource inheritedResource : inheritedResources) {
            Validate.isTrue(
                inheritedResourceTypes.contains(inheritedResource.getType()),
                "inherited resources cannot contain non-inherited resource type " + inheritedResource.getType()
            );
        }
    }
}
