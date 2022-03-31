package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponseSerializer;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class RepositoryResponsePersistenceConverter implements AttributeConverter<RepositoryResponse, String> {

    @Override
    public String convertToDatabaseColumn(RepositoryResponse attributeValue) {
        return attributeValue == null ? null : new RepositoryResponseSerializer().serialize(attributeValue);
    }

    @Override
    public RepositoryResponse convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : new RepositoryResponseSerializer().deserialize(databaseValue);
    }
}
