package net.ripe.rpki.ripencc.support.persistence;

import org.joda.time.Instant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;

@Converter(autoApply = true)
public class InstantPersistenceConverter implements AttributeConverter<Instant, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(Instant attributeValue) {
        return attributeValue == null ? null : new Timestamp(attributeValue.getMillis());
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp databaseValue) {
        return databaseValue == null ? null : Instant.ofEpochMilli(databaseValue.getTime());
    }
}
