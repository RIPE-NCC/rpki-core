package net.ripe.rpki.config.actuator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Endpoint(id="active-node")
public class ActiveNodeEndpoint {
    @Autowired
    private ActiveNodeService activeNodeService;

    @ReadOperation()
    public ActiveNodeStatus activeNodeStatus() {
        return ActiveNodeStatus.builder()
                .activeNodeName(activeNodeService.getActiveNodeName())
                .active(activeNodeService.isActiveNode())
                .build();
    }

    @Value
    @Builder
    public static class ActiveNodeStatus {
        /** Name of the currently active node */
        public final String activeNodeName;
        /** Status of this node */
        public final boolean active;
    }
}
