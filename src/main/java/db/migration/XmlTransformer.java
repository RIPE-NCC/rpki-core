package db.migration;

import org.apache.commons.lang.Validate;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * XML transformer (only) used for (old) database migrations.
 *
 * Default/package only visibility.
 */
final class XmlTransformer {

    private XmlTransformer() {
    }

    public static String transform(String xsl, String xml) throws TransformerException {
        Validate.notNull(xsl, "XSL must be present");
        Validate.notNull(xml, "XML must be present");
        Source xmlSource = new StreamSource(newInputStream(xml));
        Source xslSource = new StreamSource(newInputStream(xsl));

        // Explicitly disable external entity support
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = transformerFactory.newTransformer(xslSource);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        transformer.transform(xmlSource, new StreamResult(os));
        return os.toString();
    }

    private static InputStream newInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }
}
