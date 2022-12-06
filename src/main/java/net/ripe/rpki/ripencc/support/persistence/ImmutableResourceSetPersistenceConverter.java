package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.ipresource.ImmutableResourceSet;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class ImmutableResourceSetPersistenceConverter implements AttributeConverter<ImmutableResourceSet, String> {

    @Override
    public String convertToDatabaseColumn(ImmutableResourceSet attributeValue) {
        return attributeValue == null ? null : attributeValue.toString();
    }

    @Override
    public ImmutableResourceSet convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : ImmutableResourceSet.parse(databaseValue);
    }
}
