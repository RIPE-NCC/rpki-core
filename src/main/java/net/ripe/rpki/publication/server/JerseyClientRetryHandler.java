package net.ripe.rpki.publication.server;

//import com.sun.jersey.api.client.ClientHandlerException;
//import com.sun.jersey.api.client.ClientRequest;
//import com.sun.jersey.api.client.ClientResponse;
//import com.sun.jersey.api.client.filter.ClientFilter;

import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

public class JerseyClientRetryHandler implements ContainerRequestFilter {
    private final int maxRetries = 3;

//    @Override
//    public ClientResponse handle(ClientRequest clientResponse) throws ClientHandlerException {
//        Throwable lastCause = null;
//        int i = 0;
//        while(i++ < maxRetries) {
//            try {
//                return getNext().handle(clientResponse);
//            } catch (ClientHandlerException e) {
//                lastCause = e.getCause();
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException ie) {
//                    throw new ClientHandlerException("Interrupted", ie);
//                }
//            }
//        }
//        throw new ClientHandlerException("Connection retry limit exceeded for URL "+ clientResponse.getURI(), lastCause);
//    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) {

    }
}
