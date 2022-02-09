package net.ripe.rpki.ripencc.ui.daemon.health;

import java.util.List;

public class HealthServlet extends GenericHealthServlet {
    protected List<Health.Check> getHealthChecks() {
        return springContext.getBean(HealthChecks.class).getChecks();
    }
}
