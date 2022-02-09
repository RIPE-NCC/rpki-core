package net.ripe.rpki.ui.configuration;

public interface UiConfiguration {

    String DEPLOYMENT_ENVIRONMENT_BANNER_IMAGE = "static.image.deployment.environment";

	String getDeploymentEnvironmentBannerImage();

    boolean showEnvironmentBanner();

    boolean showEnvironmentStripe();
}
