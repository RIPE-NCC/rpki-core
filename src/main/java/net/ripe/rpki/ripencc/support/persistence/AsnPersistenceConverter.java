package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.ipresource.Asn;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigInteger;

@Converter(autoApply = true)
public class AsnPersistenceConverter implements AttributeConverter<Asn, BigInteger> {

    @Override
    public BigInteger convertToDatabaseColumn(Asn attributeValue) {
        return attributeValue == null ? null : attributeValue.getValue();
    }

    @Override
    public Asn convertToEntityAttribute(BigInteger databaseValue) {
        return databaseValue == null ? null : new Asn(databaseValue);
    }
}
