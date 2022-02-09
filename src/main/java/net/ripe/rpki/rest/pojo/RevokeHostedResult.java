package net.ripe.rpki.rest.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevokeHostedResult {
    public String caName;
    public Boolean revoked;
    public String error;
}
