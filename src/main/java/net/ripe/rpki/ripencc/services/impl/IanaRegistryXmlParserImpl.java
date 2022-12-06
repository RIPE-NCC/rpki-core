package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.rpki.commons.util.XML;
import net.ripe.rpki.server.api.ports.IanaRegistryXmlParser;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Component
public class IanaRegistryXmlParserImpl implements IanaRegistryXmlParser {

    private final String ianaAsnDelegations;
    private final String ianaIpv4Delegations;
    private final String ianaIpv6Delegations;

    private static final Map<MajorityRir, String> MAJORITY_RIR_KEYS;

    static {
        MAJORITY_RIR_KEYS = new HashMap<>();
        MAJORITY_RIR_KEYS.put(MajorityRir.AFRINIC, "whois.afrinic.net");
        MAJORITY_RIR_KEYS.put(MajorityRir.APNIC, "whois.apnic.net");
        MAJORITY_RIR_KEYS.put(MajorityRir.ARIN, "whois.arin.net");
        MAJORITY_RIR_KEYS.put(MajorityRir.LACNIC, "whois.lacnic.net");
        MAJORITY_RIR_KEYS.put(MajorityRir.RIPE, "whois.ripe.net");
    }

    @Inject
    public IanaRegistryXmlParserImpl(@Value("${iana.ASN.delegations}") String ianaAsnDelegations,
                                     @Value("${iana.IPv4.delegations}") String ianaIpv4Delegations,
                                     @Value("${iana.IPv6.delegations}") String ianaIpv6Delegations) {
        this.ianaAsnDelegations = ianaAsnDelegations;
        this.ianaIpv4Delegations = ianaIpv4Delegations;
        this.ianaIpv6Delegations = ianaIpv6Delegations;
    }

    @Override
    public ImmutableResourceSet getRirResources(MajorityRir rir) {
        InputStream asnXml = read(ianaAsnDelegations);
        InputStream ipv4Xml = read(ianaIpv4Delegations);
        InputStream ipv6Xml = read(ianaIpv6Delegations);

        Document asnDoc = parseXmlFile(asnXml);
        Document ipv4Doc = parseXmlFile(ipv4Xml);
        Document ipv6Doc = parseXmlFile(ipv6Xml);

        NodeList asnNodes16 = runXPath(asnDoc, "/registry/registry[@id='as-numbers-1']/record/number[ancestor::record/whois/text() = '" + MAJORITY_RIR_KEYS.get(rir) +"']");
        NodeList asnNodes32 = runXPath(asnDoc, "/registry/registry[@id='as-numbers-2']/record/number[ancestor::record/whois/text() = '" + MAJORITY_RIR_KEYS.get(rir) +"']");
        NodeList ipv4Nodes = runXPath(ipv4Doc, "/registry/record/prefix[ancestor::record/whois/text() = '" + MAJORITY_RIR_KEYS.get(rir) + "']");
        NodeList ipv6Nodes = runXPath(ipv6Doc, "/registry/record/prefix[ancestor::record/whois/text() = '" + MAJORITY_RIR_KEYS.get(rir) + "']");

        ImmutableResourceSet.Builder builder = new ImmutableResourceSet.Builder();
        extractIpResources(builder, asnNodes16);
        extractIpResources(builder, asnNodes32);
        extractIpResources(builder, ipv4Nodes);
        extractIpResources(builder, ipv6Nodes);
        return builder.build();
    }

    private InputStream read(String ianaDelegationsLocation) {
        try {
            if (ianaDelegationsLocation.startsWith("http")) {
                return new URL(ianaDelegationsLocation).openStream();
            } else { // Allow using local files in development mode
                return FileUtils.openInputStream(new File(ianaDelegationsLocation));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File '" + ianaDelegationsLocation + "' does not exist or cannot be read by the application");
        }
    }

    private Document parseXmlFile(InputStream xml) {
        try {
            Document document = XML.newNonNamespaceAwareDocumentBuilder().parse(xml);
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private NodeList runXPath(Document document, String xpath) {
        try {
            XPathExpression expr = XPathFactory.newInstance().newXPath().compile(xpath);
            return (NodeList) expr.evaluate(document, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractIpResources(ImmutableResourceSet.Builder builder, NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            builder.add(IpResource.parse(nodes.item(i).getTextContent()));
        }
    }
}
