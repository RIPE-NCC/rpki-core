package net.ripe.rpki.ui.configuration;


public class UiConfigurationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UiConfigurationException(String msg) {
		super(msg);
	}

	public UiConfigurationException(String msg, Throwable t) {
		super(msg, t);
	}

}
