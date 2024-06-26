package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.ipresource.IpResourceSet;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
