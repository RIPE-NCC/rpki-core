package net.ripe.rpki.ripencc.services.impl;

import com.google.common.collect.Lists;
import net.ripe.rpki.ripencc.services.impl.CustomerServiceClient.MemberSummary;
import net.ripe.rpki.server.api.security.RunAsUser;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.ripe.rpki.server.api.security.RunAsUser.ADMIN;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class RipeNccInternalNamePresenterTest {

    private static final UUID TEST_USER_ID = UUID.fromString("3aabae33-af87-43dc-8ff9-e34e4295b96d");
    private static final String TEST_USER_PRINCIPAL = TEST_USER_ID.toString();

    private ScheduledExecutorService scheduledExecutorService;
    private CustomerServiceClient customerServiceClient;
    private AuthServiceClient authServiceClient;
    private RipeNccInternalNamePresenter subject;

    @Before
    public void setUp() {
        scheduledExecutorService = mock(ScheduledExecutorService.class);
        customerServiceClient = mock(CustomerServiceClient.class);
        authServiceClient = mock(AuthServiceClient.class);
        subject = new RipeNccInternalNamePresenter(authServiceClient, customerServiceClient, scheduledExecutorService);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldReturnPrincipalIfNoPortalUserFound() {
        when(authServiceClient.getUserEmail(TEST_USER_ID)).thenReturn(Optional.empty());

        String resolvedUserName = subject.humanizeUserPrincipal(TEST_USER_PRINCIPAL);

        assertEquals(TEST_USER_ID.toString(), resolvedUserName);
    }

    @Test
    public void shouldUseOriginalPrincipalIfPrincipalIsNoUuid() {
        String resolvedUserName = subject.humanizeUserPrincipal("nemo");

        assertEquals("nemo", resolvedUserName);
    }

    @Test
    public void shouldUseOriginalPrincipalAnyExceptionOccursLookingUp() {
        when(authServiceClient.getUserEmail(TEST_USER_ID)).thenThrow(new RuntimeException("TEST"));

        String resolvedUserName = subject.humanizeUserPrincipal(TEST_USER_PRINCIPAL);

        assertEquals(TEST_USER_PRINCIPAL, resolvedUserName);
    }

    @Test
    public void shouldResolve() {
        final String userEmail = "test@example.com";
        when(authServiceClient.getUserEmail(TEST_USER_ID)).thenReturn(Optional.of(userEmail));

        String resolvedUserName = subject.humanizeUserPrincipal(TEST_USER_PRINCIPAL);

        assertEquals(userEmail, resolvedUserName);
    }

    @Test
    public void shouldResolveSameUserIdOnlyOnce() {
        final String userEmail = "test@example.com";
        when(authServiceClient.getUserEmail(TEST_USER_ID)).thenReturn(Optional.of(userEmail));

        subject.humanizeUserPrincipal(TEST_USER_PRINCIPAL);
        subject.humanizeUserPrincipal(TEST_USER_PRINCIPAL);

        verify(authServiceClient, times(1)).getUserEmail(TEST_USER_ID);
    }

    @Test
    public void shouldResolveSystemUser() {
        RunAsUser admin = ADMIN;
        String resolvedUserName = subject.humanizeUserPrincipal(admin.getCertificationUserId().getId().toString());

        assertEquals(admin.getFriendlyName(), resolvedUserName);
    }

    @Test
    public void should_schedule_task_to_fill_registry_cache() {
        ArgumentCaptor<Runnable> fillRegistryCommand = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService).scheduleAtFixedRate(fillRegistryCommand.capture(), isA(Long.class), isA(Long.class), isA(TimeUnit.class));
        verify(customerServiceClient, times(0)).findAllMemberSummaries();

        fillRegistryCommand.getValue().run();

        verify(customerServiceClient, times(1)).findAllMemberSummaries();
    }

    @Test
    public void should_reload_the_cache_after_one_hour() {
        Instant started = new Instant();
        DateTimeUtils.setCurrentMillisFixed(started.getMillis());

        ArgumentCaptor<Runnable> fillRegistryCommand = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService).scheduleAtFixedRate(fillRegistryCommand.capture(), isA(Long.class), isA(Long.class), isA(TimeUnit.class));

        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(1)).findAllMemberSummaries();

        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(1)).findAllMemberSummaries();

        DateTimeUtils.setCurrentMillisFixed(started.plus(RipeNccInternalNamePresenter.MEMBER_CACHE_REFRESH_INTERVAL).getMillis());
        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(2)).findAllMemberSummaries();
    }

    @Test
    public void should_reload_the_cache_immediately_after_retrieval_failure() {
        Instant started = new Instant();
        DateTimeUtils.setCurrentMillisFixed(started.getMillis());

        ArgumentCaptor<Runnable> fillRegistryCommand = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService).scheduleAtFixedRate(fillRegistryCommand.capture(), isA(Long.class), isA(Long.class), isA(TimeUnit.class));

        // Initial load.
        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(1)).findAllMemberSummaries();

        // Throw error on reload, resetting last lookup time.
        when(customerServiceClient.findAllMemberSummaries()).thenThrow(new RuntimeException("TEST")).thenReturn(Collections.emptyList());
        DateTimeUtils.setCurrentMillisFixed(started.plus(Duration.standardMinutes(61)).getMillis());
        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(2)).findAllMemberSummaries();

        // New reload should retry call immediately, instead of waiting refresh interval.
        fillRegistryCommand.getValue().run();
        verify(customerServiceClient, times(3)).findAllMemberSummaries();
    }

    @Test
    public void should_use_organisationId_as_humanized_ca_name_for_organisations_principals(){
        X500Principal principal = new X500Principal("O=ORG-1");
        String name = subject.humanizeCaName(principal);

        assertEquals("ORG-1", name);
    }

    @Test
    public void should_use_principal_name_as_humanized_ca_name_for_missing_members_on_cache(){
        X500Principal principal = new X500Principal("CN=123");
        String name = subject.humanizeCaName(principal);

        assertEquals("CN=123", name);
    }

    @Test
    public void should_use_regId_as_humanized_ca_name_for_members_on_cache(){
        MemberSummary memberSummary = new MemberSummary(123, "zz.example", "zz.example registry");
        when(customerServiceClient.findAllMemberSummaries()).thenReturn(Lists.newArrayList(memberSummary));

        populateCache();

        X500Principal principal = new X500Principal("CN=123");
        String name = subject.humanizeCaName(principal);

        assertEquals("zz.example", name);
    }

    private void populateCache() {
        Instant started = new Instant();
        DateTimeUtils.setCurrentMillisFixed(started.getMillis());

        ArgumentCaptor<Runnable> fillRegistryCommand = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService).scheduleAtFixedRate(fillRegistryCommand.capture(), isA(Long.class), isA(Long.class), isA(TimeUnit.class));
        // Initial load.
        fillRegistryCommand.getValue().run();
    }
}
