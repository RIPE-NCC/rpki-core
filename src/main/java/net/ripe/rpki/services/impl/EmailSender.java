package net.ripe.rpki.services.impl;

import java.util.Map;

public interface EmailSender {

    void sendEmail(String emailTo, String subject, String nameOfTemplate, Map<String, Object> parameters);

}
