package net.ripe.rpki.ripencc.services.impl;

import org.junit.Before;
import org.junit.Test;

public class RestCustomerServiceClientTest {

    private RestCustomerServiceClient subject;

    @Before
    public void setUp() {
        subject = new RestCustomerServiceClient("http://wrong.url.net", "",1, 1);
    }

    @Test(expected = Exception.class)
    public void should_throw_a_ClientHandlerException_if_service_is_not_able_give_a_response() {
        subject.isAvailable();
    }
}
