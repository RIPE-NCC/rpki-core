package net.ripe.rpki.domain.bgpsec;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.ripe.rpki.rest.exception.BadRouterIdException;

import java.io.IOException;

@JsonSerialize(using = ToJSON.class)
@JsonDeserialize(using = FromJSON.class)
public record RouterId(Long value) {
    public static final RouterId ZERO = new RouterId(0L);

    public RouterId {
        if (value == null || value < 0) {
            throw new BadRouterIdException("Router ID must be a positive integer, actual: " + value);
        }
    }

    public RouterId(String value) {
        this(Long.parseLong(value));
    }
}

class ToJSON extends JsonSerializer<RouterId> {
    @Override
    public void serialize(RouterId routerId, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(routerId.value());
    }
}

class FromJSON extends JsonDeserializer<RouterId> {
    @Override
    public RouterId deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        if (!jsonParser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw new BadRouterIdException("Router ID must be a positive integer");
        }
        return new RouterId(jsonParser.getLongValue());
    }
}
