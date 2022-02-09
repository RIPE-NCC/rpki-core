package net.ripe.rpki.services.impl;

import net.ripe.rpki.server.api.configuration.Environment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EmailSenderBeanTest {

    @Mock
    private MailSender mailSender;
    private ArgumentCaptor<SimpleMailMessage> messageCapture;
    private EmailSenderBean subject;

    @Before
    public void setUp() {
        messageCapture = ArgumentCaptor.forClass(SimpleMailMessage.class);

        subject = new EmailSenderBean(mailSender);

        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, "junit");
    }

    @After
    public void tearDown() {
        System.setProperty(Environment.APPLICATION_ENVIRONMENT_KEY, Environment.LOCAL_ENV_NAME);
    }

    @Test
    public void shouldSendEmail() {
        String emailTo = "email@example.com";
        String text = "This is a test: value";

        subject.sendEmail(emailTo, "test subject", "email-templates/test-template.vm", Collections.singletonMap("param", "value"));

        verify(mailSender).send(messageCapture.capture());
        assertEquals(emailTo, messageCapture.getValue().getTo()[0]);
        assertEquals("test subject", messageCapture.getValue().getSubject());
        assertEquals(text, messageCapture.getValue().getText());
    }
}
