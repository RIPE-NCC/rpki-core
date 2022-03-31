package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class PublisherRequestPersistenceConverter implements AttributeConverter<PublisherRequest, String> {

    @Override
    public String convertToDatabaseColumn(PublisherRequest attributeValue) {
        return attributeValue == null ? null : new PublisherRequestSerializer().serialize(attributeValue);
    }

    @Override
    public PublisherRequest convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : new PublisherRequestSerializer().deserialize(databaseValue);
    }
}
