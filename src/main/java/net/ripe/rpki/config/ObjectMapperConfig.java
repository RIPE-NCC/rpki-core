package net.ripe.rpki.config;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.ripe.ipresource.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;


@Configuration
public class ObjectMapperConfig {

    @Bean
    public Module ipresource() {
        SimpleModule result = new SimpleModule("ipresource");
        result.addSerializer(IpResource.class, ToStringSerializer.instance);
        result.addSerializer(IpResourceSet.class, ToStringSerializer.instance);
        result.addSerializer(ImmutableResourceSet.class, ToStringSerializer.instance);
        result.addDeserializer(Asn.class, deserialize(Asn.class, Asn::parse));
        result.addDeserializer(IpAddress.class, deserialize(IpAddress.class, IpAddress::parse));
        result.addDeserializer(Ipv4Address.class, deserialize(Ipv4Address.class, Ipv4Address::parse));
        result.addDeserializer(Ipv6Address.class, deserialize(Ipv6Address.class, Ipv6Address::parse));
        result.addDeserializer(IpRange.class, deserialize(IpRange.class, IpRange::parse));
        result.addDeserializer(IpResourceRange.class, deserialize(IpResourceRange.class, IpResourceRange::parse));
        result.addDeserializer(IpResource.class, deserialize(IpResource.class, IpResource::parse));
        result.addDeserializer(IpResourceSet.class, deserialize(IpResourceSet.class, IpResourceSet::parse));
        return result;
    }

    private static <T> JsonDeserializer<T> deserialize(Class<T> type, Function<String, T> parse) {
        return new FromStringDeserializer<T>(type) {
            @Override
            protected T _deserialize(String value, DeserializationContext ctxt) {
                return parse.apply(value);
            }
        };
    }
}
