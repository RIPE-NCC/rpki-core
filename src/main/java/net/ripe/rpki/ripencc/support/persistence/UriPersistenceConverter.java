package net.ripe.rpki.ripencc.support.persistence;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.net.URI;

@Converter(autoApply = true)
public class UriPersistenceConverter implements AttributeConverter<URI, String> {

    @Override
    public String convertToDatabaseColumn(URI attributeValue) {
        return attributeValue == null ? null : attributeValue.toASCIIString();
    }

    @Override
    public URI convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : URI.create(databaseValue);
    }
}
