package net.ripe.rpki.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddedInformationAccessDescriptor {
    @Column
    @NotNull
    private ASN1ObjectIdentifier method;

    @Column
    @NotNull
    private URI location;

    public static EmbeddedInformationAccessDescriptor of(X509CertificateInformationAccessDescriptor descriptor) {
        return new EmbeddedInformationAccessDescriptor(descriptor.getMethod(), descriptor.getLocation());
    }

    public X509CertificateInformationAccessDescriptor toDescriptor() {
        return new X509CertificateInformationAccessDescriptor(method, location);
    }
}
