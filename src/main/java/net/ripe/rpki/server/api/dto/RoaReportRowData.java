package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import org.apache.commons.lang.Validate;
import org.joda.time.Instant;

import javax.security.auth.x500.X500Principal;
import java.util.List;

public class RoaReportRowData extends ValueObjectSupport {

    private static final long serialVersionUID = 1L;

    private final X500Principal caName;

    private final boolean caWithRoaSpecification;

    private final boolean caWithRoaCommand;

    private final List<String> principals;

    private final Instant caCreatedAt;

    private final List<AnnouncedRoute> invalidAsns;

    private final List<AnnouncedRoute> invalidLengths;

    private final List<AnnouncedRoute> unknownAnnouncedRoutes;

    private final List<AnnouncedRoute> validAnnouncedRoutes;


    public RoaReportRowData(X500Principal caName,
                            Instant caCreatedAt,
                            boolean caWithRoaSpecification,
                            boolean caWithRoaCommand,
                            List<AnnouncedRoute> invalidAsns,
                            List<AnnouncedRoute> invalidLengths,
                            List<AnnouncedRoute> unknownAnnouncedRoutes,
                            List<AnnouncedRoute> validAnnouncedRoutes,
                            List<String> principals) {
        Validate.notNull(caName, "CA name is required");
        Validate.notNull(principals, "principals is required");
        Validate.notNull(caCreatedAt, "caCreatedAt");

        this.caName = caName;
        this.caWithRoaSpecification = caWithRoaSpecification;
        this.caWithRoaCommand = caWithRoaCommand;
        this.principals = principals;
        this.caCreatedAt = caCreatedAt;
        this.invalidAsns = invalidAsns;
        this.invalidLengths = invalidLengths;
        this.unknownAnnouncedRoutes = unknownAnnouncedRoutes;
        this.validAnnouncedRoutes = validAnnouncedRoutes;
    }

    public X500Principal getCaName() {
        return caName;
    }

    public Instant getCaCreatedAt() {
        return caCreatedAt;
    }

    public boolean isCaWithRoaSpecification() {
        return caWithRoaSpecification;
    }

    public boolean isCaWithRoaCommand() {
        return caWithRoaCommand;
    }

    public List<String> getPrincipals() {
        return principals;
    }

    public List<AnnouncedRoute> getInvalidAsns() {
        return invalidAsns;
    }

    public List<AnnouncedRoute> getInvalidLengths() {
        return invalidLengths;
    }

    public List<AnnouncedRoute> getUnknownAnnouncedRoutes() {
        return unknownAnnouncedRoutes;
    }

    public List<AnnouncedRoute> getValidAnnouncedRoutes() {
        return validAnnouncedRoutes;
    }
}
