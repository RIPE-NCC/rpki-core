package net.ripe.rpki.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.format.ISODateTimeFormat.dateTimeParser;

@Provider
@Component
public class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private final ObjectMapper objectMapper;

    public ObjectMapperProvider() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.registerModule(createDateTimeModule());
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }

    private Module createDateTimeModule() {
        SimpleModule module = new SimpleModule("Joda DateTime");

        module.addSerializer(DateTime.class, new JsonSerializer<DateTime>() {
            @Override
            public void serialize(DateTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                jgen.writeString(value.toDateTime(UTC).toString());
            }
        });

        module.addDeserializer(DateTime.class, new JsonDeserializer<DateTime>() {
            private final DateTimeFormatter parser = dateTimeParser().withZoneUTC();

            @Override
            public DateTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                if (VALUE_STRING.equals(jp.getCurrentToken())) {
                    return parser.parseDateTime(jp.getValueAsString());
                } else {
                    throw ctxt.wrongTokenException(jp, DateTime.class, VALUE_STRING, "Expected a String");
                }
            }
        });

        return module;
    }
}
