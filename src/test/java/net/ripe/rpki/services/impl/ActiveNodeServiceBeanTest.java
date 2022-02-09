package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.domain.property.PropertyEntity;
import net.ripe.rpki.domain.property.PropertyEntityRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static net.ripe.rpki.services.impl.ActiveNodeServiceBean.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class ActiveNodeServiceBeanTest {

    private ActiveNodeServiceBean subject;

    private PropertyEntityRepository propertyEntityRepository;
    private static final String TEST_ACTIVE_NODE_NAME = "active-node";

    @Rule
    public FixedDateRule now = new FixedDateRule("20080501");

    @Before
    public void setUp() {
        propertyEntityRepository = mock(PropertyEntityRepository.class);
        subject = new ActiveNodeServiceBean(propertyEntityRepository, new SimpleMeterRegistry());
    }

    @Test
    public void shouldReturnActiveNode() {
        PropertyEntity propertyEntity = new PropertyEntity(ACTIVE_NODE_KEY, TEST_ACTIVE_NODE_NAME);

        when(propertyEntityRepository.findByKey(ACTIVE_NODE_KEY)).thenReturn(propertyEntity);

        assertEquals(TEST_ACTIVE_NODE_NAME, subject.getActiveNodeName());
    }

    @Test
    public void shouldReturnNullForNonExistingActiveNode() {
        when(propertyEntityRepository.findByKey(ACTIVE_NODE_KEY)).thenReturn(null);

        assertNull(subject.getActiveNodeName());
    }

    @Test
    public void shouldSetActiveNode() {
        PropertyEntity propertyEntity = new PropertyEntity(ACTIVE_NODE_KEY, "new-active-node");

        subject.setActiveNodeName("new-active-node");

        verify(propertyEntityRepository).add(refEq(propertyEntity));
        verify(propertyEntityRepository, never()).merge(any(PropertyEntity.class));
    }

    @Test
    public void shouldChangeExistingProperty() {
        PropertyEntity oldPropertyEntity = new PropertyEntity(ACTIVE_NODE_KEY, TEST_ACTIVE_NODE_NAME);
        PropertyEntity newPropertyEntity = new PropertyEntity(ACTIVE_NODE_KEY, "new-active-node");

        when(propertyEntityRepository.findByKey(ACTIVE_NODE_KEY)).thenReturn(oldPropertyEntity);

        assertEquals(TEST_ACTIVE_NODE_NAME, subject.getActiveNodeName());
        subject.setActiveNodeName("new-active-node");
        assertEquals("new-active-node", subject.getActiveNodeName());
        verify(propertyEntityRepository).merge(refEq(newPropertyEntity));
    }
}
