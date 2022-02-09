package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.ui.util.WicketUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.wicket.markup.html.WebResource;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.resource.ByteArrayResource;

public class ShowProvisioningDetailsPanel extends Panel {

    private static final long serialVersionUID = 1L;

    public ShowProvisioningDetailsPanel(String id, ProvisioningIdentityCertificate identityCertificate) {
        super(id);

        // it's always true - otherwise this panel wouldn't be shown
        add(WicketUtils.getStatusImage("initted", identityCertificate != null));

        byte[] certificateEncoded = identityCertificate.getEncoded();
        byte[] encoded = Base64.encodeBase64(certificateEncoded);

        add(new ResourceLink<WebResource>("downloadIssuerIdentity", new ByteArrayResource("application/x-x509-ca-cert", encoded, "issuer-idcert.cer")));
    }

}
