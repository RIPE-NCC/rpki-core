package net.ripe.rpki.server.api.configuration;

import com.google.common.base.Preconditions;

public final class Environment {

    public static final String LOCAL_ENV_NAME = "local";
    public static final String APPLICATION_ENVIRONMENT_KEY = "APPLICATION_ENVIRONMENT";

    private Environment() {
    }

    static {
        load();
    }

    public static void load() {
        if (getEnvironmentName() == null) {
            System.setProperty(APPLICATION_ENVIRONMENT_KEY, LOCAL_ENV_NAME);
        }
    }

    public static Integer getPortNumber() {
        return Integer.valueOf(System.getProperty("port", "8080"));
    }

    public static String getInstanceName() {
        final String instanceName = System.getProperty("instance.name", isLocal() ? "local" : null);
        Preconditions.checkNotNull(instanceName, "Could not find a value for System property 'instance.name'");
        return instanceName;
    }

    public static String getEnvironmentName() {
        return System.getProperty(APPLICATION_ENVIRONMENT_KEY);
    }

    public static boolean isLocal() {
        return LOCAL_ENV_NAME.equals(getEnvironmentName());
    }

    public static String getSpringProfile() {
        return System.getProperty("spring.profiles.active", "-");
    }

    public static boolean isProduction() {
        return "production".equalsIgnoreCase(getEnvironmentName());
    }

    public static boolean isPilot() {
        return "pilot".equalsIgnoreCase(getEnvironmentName());
    }

    public static boolean isDev() {
        return "dev".equalsIgnoreCase(getEnvironmentName());
    }

    public static boolean isTest() {
        return "test".equalsIgnoreCase(getEnvironmentName()) || "test".equalsIgnoreCase(getSpringProfile());
    }
}
