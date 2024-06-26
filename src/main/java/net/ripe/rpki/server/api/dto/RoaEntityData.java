package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import jakarta.validation.constraints.NotNull;

/**
 * DTO object for actual ROA objects (as opposed to specification objects)
 */
public class RoaEntityData extends ValueObjectSupport {

    @NotNull
    private RoaCms roaCms;

    @NotNull
    private Long roaEntityId;

    @NotNull
    private Long resourceCertificateId;

    @NotNull
    private String filename;

	public RoaEntityData() {
	}

	public RoaEntityData(RoaCms roaCms, Long roaEntityId, Long resourceCertificateId, String filename) {
		this.roaCms = roaCms;
		this.roaEntityId = roaEntityId;
		this.resourceCertificateId = resourceCertificateId;
        this.filename = filename;
	}

	public RoaCms getRoaCms() {
		return roaCms;
	}

	public Long getRoaEntityId() {
		return roaEntityId;
	}

	public Long getResourceCertificateId() {
		return resourceCertificateId;
	}

	public String getFilename() {
        return filename;
    }
}
