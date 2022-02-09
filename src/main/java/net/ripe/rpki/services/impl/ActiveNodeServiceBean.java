package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.domain.property.PropertyEntity;
import net.ripe.rpki.domain.property.PropertyEntityRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;


@Service
@Transactional
public class ActiveNodeServiceBean implements ActiveNodeService {
    public static final String ACTIVE_NODE_KEY = "activeNode";

    private PropertyEntityRepository propertyEntityRepository;

    @Inject
    public ActiveNodeServiceBean(PropertyEntityRepository propertyEntityRepository, MeterRegistry meterRegistry) {
        this.propertyEntityRepository = propertyEntityRepository;

        // Reference is kept through the meter registry
        Gauge.builder("rpkicore_is_active_node", () -> this.isActiveNode(getCurrentNodeName()) ? 1 : 0)
                .description("Is the current node the active node")
                .tag("node", getCurrentNodeName())
                .register(meterRegistry);
    }

    @Override
    public void activateCurrentNode() {
        setActiveNodeName(getCurrentNodeName());
    }

    @Override
    public String getCurrentNodeName() {
        return Environment.getInstanceName();
    }

	@Override
	public boolean isActiveNode(String nodeName) {
		return nodeName.equals(getActiveNodeName());
	}

	@Override
	public String getActiveNodeName() {
		return getValueForKey(ACTIVE_NODE_KEY);
	}

	@Override
	public void setActiveNodeName(String nodeName) {
		setValueForKey(ACTIVE_NODE_KEY, nodeName);
	}


    private String getValueForKey(String key) {
        PropertyEntity property = propertyEntityRepository.findByKey(key);
        return property != null? property.getValue() : null;
    }

    private void setValueForKey(String key, String value) {
        PropertyEntity property = propertyEntityRepository.findByKey(key);
        if (property == null) {
            addProperty(key, value);
        } else {
            property.setValue(value);
            propertyEntityRepository.merge(property);
        }
    }

    private void addProperty(String key, String value) {
        propertyEntityRepository.add(new PropertyEntity(key, value));
    }

}
