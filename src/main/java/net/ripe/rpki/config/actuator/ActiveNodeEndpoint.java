package net.ripe.rpki.config.actuator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@AllArgsConstructor
@Component
@RestControllerEndpoint(id="active-node")
public class ActiveNodeEndpoint {
    @Autowired
    private ActiveNodeService activeNodeService;

    @GetMapping()
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
