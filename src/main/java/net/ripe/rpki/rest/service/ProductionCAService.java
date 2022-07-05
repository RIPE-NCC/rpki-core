package net.ripe.rpki.rest.service;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static com.google.common.collect.ImmutableMap.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;

@Slf4j
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Component
@Path("/prod/ca")
@Tag(name = "/prod/ca", description = "Operations on Production CA")
@Scope("prototype")
public class ProductionCAService {
    @Autowired
    private ActiveNodeService activeNodeService;

    @Autowired
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    private ResourceLookupService resourceCache;

    @Autowired
    private CommandService commandService;

    @Autowired
    private RepositoryConfiguration certificationConfiguration;

    @POST
    @Path("create")
    @Operation(summary = "Create Production CA certificate")
    public Response create() {
        try {
            VersionedId caId = commandService.getNextId();
            commandService.execute(new CreateRootCertificateAuthorityCommand(caId));
            activeNodeService.activateCurrentNode();

            return Response.status(NO_CONTENT).build();
        } catch (Exception e) {
            log.error("", e);
            return Response.status(BAD_REQUEST).entity(of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("generate-all-resources-sign-request")
    @Operation(summary = "Generate Sign Request for New resources")
    public Response generateAllResourcesSignRequest() {
        try {
            final X500Principal productionCaName = certificationConfiguration.getProductionCaPrincipal();
            final CertificateAuthorityData productionCaData = certificateAuthorityViewService.findCertificateAuthorityByName(productionCaName);

            commandService.execute(new AllResourcesCaResourcesCommand(productionCaData.getVersionedId()));
            return Response.status(NO_CONTENT).build();
        } catch (Exception e) {
            log.error("", e);
            return Response.status(BAD_REQUEST).entity(of("error", e.getMessage())).build();
        }
    }
}
