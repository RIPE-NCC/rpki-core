package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaEntityData;

import java.util.List;

/**
 * Interface for managing ROAs and anything related to them (configuration,
 * creation, publishing, etc).
 */
public interface RoaViewService {

    List<RoaEntityData> findAllRoasForCa(Long caId);

    RoaConfigurationData getRoaConfiguration(long caId);
}
