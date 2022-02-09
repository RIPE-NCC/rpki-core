package net.ripe.rpki.server.api.services.system;

public interface ActiveNodeService {

    void activateCurrentNode();

    String getCurrentNodeName();

	boolean isActiveNode(String nodeName);

	String getActiveNodeName();

	void setActiveNodeName(String nodeName);
}
