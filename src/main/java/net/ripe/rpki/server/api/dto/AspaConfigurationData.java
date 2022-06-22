package net.ripe.rpki.server.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.Set;

@AllArgsConstructor
@Getter
public class AspaConfigurationData extends ValueObjectSupport {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private final Asn customerAsn;
    private final Set<AspaProviderAsnData> providerAsns;
}
