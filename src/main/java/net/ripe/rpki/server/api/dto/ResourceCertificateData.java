package net.ripe.rpki.server.api.dto;

import lombok.NonNull;
import lombok.Value;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;

import java.io.Serializable;
import java.net.URI;

@Value
public class ResourceCertificateData implements Serializable {
	@NonNull X509ResourceCertificate certificate;
    @NonNull URI publicationUri;
}
