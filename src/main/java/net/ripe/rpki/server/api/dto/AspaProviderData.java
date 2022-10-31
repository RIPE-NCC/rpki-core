package net.ripe.rpki.server.api.dto;

import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.Asn;

@Value
public class AspaProviderData {
    @NonNull
    Asn providerAsn;
    @NonNull
    AspaAfiLimit afiLimit;
}
