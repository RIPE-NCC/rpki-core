package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayloadBuilder;
import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProvisioningAuditLogServiceBeanTest {

    private ProvisioningAuditLogServiceBean provisioningAuditLogServiceBean;

    private EntityManager entityManager;

    private static final UUID TEST_USER_UUID = UUID.fromString("6e80bc78-7f56-407a-be41-3d3f76af2919");

    @Before
    public void setUp() {
        entityManager = mock(EntityManager.class);
        provisioningAuditLogServiceBean = new ProvisioningAuditLogServiceBean(entityManager);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFindRecentMessagesForCA() {
        TypedQuery<ProvisioningAuditLogEntity> query = mock(TypedQuery.class);
        when(entityManager.createQuery("select pal from ProvisioningAuditLogEntity pal " +
                "where pal.nonHostedCaUUID = :caUUID", ProvisioningAuditLogEntity.class)).thenReturn(query);
        ProvisioningCmsObject cms = mock(ProvisioningCmsObject.class);
        when(cms.getPayload()).thenReturn(new ResourceClassListQueryPayloadBuilder().build());
        ProvisioningAuditLogEntity logEntity = new ProvisioningAuditLogEntity(cms, "principal", TEST_USER_UUID);
        List<ProvisioningAuditLogEntity> result = Collections.singletonList(logEntity);
        when(query.getResultList()).thenReturn(result);

        List<ProvisioningAuditData> recentMessagesForCA = provisioningAuditLogServiceBean.findRecentMessagesForCA(TEST_USER_UUID);
        assertEquals(1, recentMessagesForCA.size());
        ProvisioningAuditData provisioningAuditData = recentMessagesForCA.get(0);
        assertEquals("principal", provisioningAuditData.getPrincipal());
        assertEquals("querying for certifiable resources", provisioningAuditData.getSummary());
    }

    @Test
    public void testLoggingListRequest() {
        ProvisioningCmsObject cms = mock(ProvisioningCmsObject.class);
        when(cms.getPayload()).thenReturn(new ResourceClassListQueryPayloadBuilder().build());
        when(cms.getEncoded()).thenReturn(new byte[] {1, 2, 3, 4 });
        ProvisioningAuditLogEntity logEntity = Mockito.spy(new ProvisioningAuditLogEntity(cms, "principal", TEST_USER_UUID));
        final byte[] request = "<?xml version='1.0' encoding='UTF-8'?><bla></bla>".getBytes(StandardCharsets.UTF_8);
        provisioningAuditLogServiceBean.log(logEntity, request);
        verify(logEntity, times(2)).getRequestMessageType();
        verify(logEntity, times(1)).getEntryUuid();
        verify(logEntity, times(1)).getPrincipal();
        verify(logEntity, times(1)).getSummary();
        verify(logEntity, times(1)).getExecutionTime();
        verify(logEntity, times(1)).getProvisioningCmsObject();
        verify(logEntity, times(1)).getNonHostedCaUUID();
        verify(logEntity, times(0)).getId();
        verify(entityManager, times(0)).persist(any(ProvisioningAuditLogEntity.class));
    }

    @Test
    public void testLoggingNotListRequest() {
        ProvisioningCmsObject cms = mock(ProvisioningCmsObject.class);
        when(cms.getPayload()).thenReturn(new CertificateRevocationRequestPayloadBuilder().build());
        when(cms.getEncoded()).thenReturn(new byte[] {1, 2, 3, 4 });
        ProvisioningAuditLogEntity logEntity = Mockito.spy(new ProvisioningAuditLogEntity(cms, "principal", TEST_USER_UUID));
        final byte[] request = "<?xml version='1.0' encoding='UTF-8'?><bla></bla>".getBytes(StandardCharsets.UTF_8);
        provisioningAuditLogServiceBean.log(logEntity, request);
        verify(logEntity, times(2)).getRequestMessageType();
        verify(logEntity, times(1)).getEntryUuid();
        verify(logEntity, times(1)).getPrincipal();
        verify(logEntity, times(1)).getSummary();
        verify(logEntity, times(1)).getExecutionTime();
        verify(logEntity, times(1)).getProvisioningCmsObject();
        verify(logEntity, times(1)).getNonHostedCaUUID();
        verify(logEntity, times(0)).getId();
        verify(entityManager, times(1)).persist(any(ProvisioningAuditLogEntity.class));
    }
}
