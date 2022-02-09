package net.ripe.rpki.ui.commons;

import java.io.IOException;

public class UserCommunicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserCommunicationException(String msg, IOException e) {
        super(msg, e);
    }

}
