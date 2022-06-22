package net.ripe.rpki.server.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

@AllArgsConstructor
@Getter
public class AspaProviderAsnData extends ValueObjectSupport {
    @JsonSerialize(using = ToStringSerializer.class)
    private final Asn asn;
    private final String prefixType;
}
