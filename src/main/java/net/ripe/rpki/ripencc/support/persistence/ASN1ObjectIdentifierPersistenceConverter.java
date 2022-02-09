package net.ripe.rpki.ripencc.support.persistence;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class ASN1ObjectIdentifierPersistenceConverter implements AttributeConverter<ASN1ObjectIdentifier, String> {

    @Override
    public String convertToDatabaseColumn(ASN1ObjectIdentifier attributeValue) {
        return attributeValue == null ? null : attributeValue.getId();
    }

    @Override
    public ASN1ObjectIdentifier convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : new ASN1ObjectIdentifier(databaseValue);
    }
}
