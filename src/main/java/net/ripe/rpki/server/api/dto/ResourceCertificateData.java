package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import org.joda.time.DateTime;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.EnumSet;

public abstract class ResourceCertificateData extends ValueObjectSupport {

	private static final long serialVersionUID = 1L;

	private final X509ResourceCertificate certificate;

	private final long id;


	public ResourceCertificateData(long id, X509ResourceCertificate certificate) {
        this.id = id;
		this.certificate = certificate;
	}

	public long getId() {
        return id;
    }

	public BigInteger getSerial() {
        return certificate.getCertificate().getSerialNumber();
    }

    public IpResourceSet getResources() {
        return certificate.getResources();
    }

    public EnumSet<IpResourceType> getInheritedResourceTypes() {
        return certificate.getInheritedResourceTypes();
    }

    public X500Principal getSubject() {
        return certificate.getSubject();
    }

    public X500Principal getIssuer() {
        return certificate.getIssuer();
    }

    public ValidityPeriod getValidityPeriod() {
        return certificate.getValidityPeriod();
    }

    public DateTime getNotValidBefore() {
        return certificate.getValidityPeriod().getNotValidBefore();
    }

    public DateTime getNotValidAfter() {
        return certificate.getValidityPeriod().getNotValidAfter();
    }

    public PublicKey getSubjectPublicKey() {
    	return certificate.getPublicKey();
    }

    public X509ResourceCertificate getCertificate() {
        return certificate;
    }

    public X509CertificateInformationAccessDescriptor[] getAia() {
        return certificate.getAuthorityInformationAccess();
    }

    public X509CertificateInformationAccessDescriptor[] getSia() {
        return certificate.getSubjectInformationAccess();
    }
}
