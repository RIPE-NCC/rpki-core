package net.ripe.rpki.ripencc.services.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.ripencc.services.impl.CustomerServiceClient.MemberSummary;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.ripe.rpki.server.api.security.RunAsUser.ADMIN;

@Component
@Slf4j
public class RipeNccInternalNamePresenter implements InternalNamePresenter {
    static final Duration MEMBER_CACHE_REFRESH_INTERVAL = Duration.standardHours(1);

    private final CustomerServiceClient customerServiceClient;
    private final AuthServiceClient authServiceClient;

    private final AtomicBoolean alreadyComplained = new AtomicBoolean(false);

    private final LoadingCache<UUID, Optional<String>> resolvedPrincipals =
        CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(new CacheLoader<UUID, Optional<String>>() {
                @Override
                public Optional<String> load(final UUID userUuid) {
                    try {
                        return authServiceClient.getUserEmail(userUuid);
                    } catch (Exception e) {
                        if (!alreadyComplained.get()) {
                            log.warn("Could not reach auth service, will not be able to show user emails: {}", e.getMessage());
                            alreadyComplained.set(true);
                        }
                        return Optional.empty();
                    }
                }
            });

    private final ConcurrentHashMap<Long, MemberSummary> members = new ConcurrentHashMap<>();

    public RipeNccInternalNamePresenter() {
        // For Wicket spring proxy generation.
        authServiceClient = null;
        customerServiceClient = null;
    }

    public RipeNccInternalNamePresenter(AuthServiceClient authServiceClient,
                                        CustomerServiceClient customerServiceClient,
                                        ScheduledExecutorService scheduledExecutorService) {
        this.authServiceClient = authServiceClient;
        this.customerServiceClient = customerServiceClient;
        scheduledExecutorService.scheduleAtFixedRate(new ReloadMembersCache(), 10, 60, TimeUnit.SECONDS);
    }

    @Inject
    public RipeNccInternalNamePresenter(AuthServiceClient authServiceClient,
                                        CustomerServiceClient customerServiceClient) {
        this(authServiceClient, customerServiceClient, Executors.newSingleThreadScheduledExecutor());
    }

    @Override
    public String humanizeCaName(X500Principal principal) {
        try {
            CaName caName = CaName.of(principal);
            if (caName.hasOrganizationId()) {
                return caName.getOrganisationId();
            }

            Long membershipId = caName.getMembershipId();
            MemberSummary member = members.get(membershipId);
            if (member == null) {
                return principal.getName();
            }
            return member.getRegId();

        } catch (RuntimeException e) {
            log.info("Could not translate CA name '" + principal + "': " + e, e);
            return String.valueOf(principal);
        }
    }

    @Override
    public String humanizeUserPrincipal(String principal) {
        if (ADMIN.getCertificationUserId().getId().toString().equals(principal)) {
            return ADMIN.getFriendlyName();
        }
        try {
            return resolvedPrincipals.get(UUID.fromString(principal)).orElse(principal);
        } catch (Exception e) {
            log.info("Couldn't parse UUID from '" + principal + "'");
            return principal;
        }
    }

    private final class ReloadMembersCache implements Runnable {
        private Instant lastLookup;

        @Override
        public void run() {
            try {
                if (lastLookup == null || !lastLookup.plus(MEMBER_CACHE_REFRESH_INTERVAL).isAfterNow()) {
                    log.info("(Re)load member cache (current size is " + members.size() + ").");
                    List<MemberSummary> memberSummaries = customerServiceClient.findAllMemberSummaries();
                    for (MemberSummary memberSummary : memberSummaries) {
                        members.put(memberSummary.getMembershipId(), memberSummary);
                    }
                    log.info("(Re)loaded member cache with " + memberSummaries.size() + " entries (updated size is " + members.size() + ").");

                    lastLookup = new Instant();
                }
            } catch (RuntimeException e) {
                log.warn("error while (re)loading member cache: " + e, e);
                lastLookup = null;
            }
        }
    }
}
