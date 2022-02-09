package net.ripe.rpki.server.api.dto;

import lombok.Value;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.support.objects.CaName;

@Value
public class CaIdentity {
    VersionedId versionedId;
    CaName caName;
}
