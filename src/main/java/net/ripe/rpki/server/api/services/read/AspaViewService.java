package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import java.util.List;

/**
 * Interface for querying ASPA and ASPA configuration.
 */
public interface AspaViewService {

    List<AspaConfigurationData> findAspaConfiguration(long caId);
}
