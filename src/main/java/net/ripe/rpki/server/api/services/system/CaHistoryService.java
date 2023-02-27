package net.ripe.rpki.server.api.services.system;

import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;

import java.util.List;

public interface CaHistoryService {
    List<CertificateAuthorityHistoryItem> getHistoryItems(CertificateAuthorityData certificateAuthority);
}
