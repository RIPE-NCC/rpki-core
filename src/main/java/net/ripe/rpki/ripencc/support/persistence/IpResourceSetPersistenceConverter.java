package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.ipresource.IpResourceSet;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.net.URI;

@Converter(autoApply = true)
public class IpResourceSetPersistenceConverter implements AttributeConverter<IpResourceSet, String> {

    @Override
    public String convertToDatabaseColumn(IpResourceSet attributeValue) {
        return attributeValue == null ? null : attributeValue.toString();
    }

    @Override
    public IpResourceSet convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : IpResourceSet.parse(databaseValue);
    }
}
