package net.ripe.rpki.ncc.core.domain.support;


/**
 * An entity is an object with a specific identity and a life-cycle. An entities
 * identity can never change. In principle there is only one instance for an
 * entity with a specific identity. Therefore, entities are not serializable.
 * 
 * See Domain Driven Design for all you ever wanted to know about entities.
 */
public interface Entity {
    Object getId();
}

