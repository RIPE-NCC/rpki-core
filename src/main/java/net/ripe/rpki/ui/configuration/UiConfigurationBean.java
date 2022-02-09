package net.ripe.rpki.ui.configuration;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UiConfigurationBean implements UiConfiguration {

    @Value("${static.image.deployment.environment}")
    private String deploymentEnvironmentBannerImage;

    @Override
    public String getDeploymentEnvironmentBannerImage() {
        return deploymentEnvironmentBannerImage;
    }

    @Override
    public boolean showEnvironmentBanner() {
        return StringUtils.isNotBlank(deploymentEnvironmentBannerImage) && !showStripe();
    }

    @Override
    public boolean showEnvironmentStripe() {
        return showStripe();
    }

    private boolean showStripe() {
        return "stripe".equalsIgnoreCase(deploymentEnvironmentBannerImage);
    }

}
