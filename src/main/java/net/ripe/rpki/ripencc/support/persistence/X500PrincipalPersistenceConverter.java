package net.ripe.rpki.ripencc.support.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.security.auth.x500.X500Principal;

@Converter(autoApply = true)
public class X500PrincipalPersistenceConverter implements AttributeConverter<X500Principal, String> {

    @Override
    public String convertToDatabaseColumn(X500Principal attributeValue) {
        return attributeValue == null ? null : attributeValue.getName();
    }

    @Override
    public X500Principal convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : new X500Principal(databaseValue);
    }
}
