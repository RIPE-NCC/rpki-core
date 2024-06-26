package net.ripe.rpki.ripencc.support.persistence;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;

@Converter(autoApply = true)
public class DateTimePersistenceConverter implements AttributeConverter<DateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(DateTime attributeValue) {
        return attributeValue == null ? null : new Timestamp(attributeValue.getMillis());
    }

    @Override
    public DateTime convertToEntityAttribute(Timestamp databaseValue) {
        return databaseValue == null ? null : new DateTime(databaseValue.getTime(), DateTimeZone.UTC);
    }
}
