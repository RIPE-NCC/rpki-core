package net.ripe.rpki.server.api.dto;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import net.ripe.ipresource.IpResourceSet;

public class XStreamResourceClassMapConverter implements Converter {

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ResourceClassMap.class.equals(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        ResourceClassMap resources = (ResourceClassMap) source;
        for (String className: resources.getClasses()) {
            writer.startNode("class");
            writer.addAttribute("name", className);
            writer.setValue(resources.getResources(className).toString());
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        ResourceClassMap result = ResourceClassMap.empty();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if (!"class".equals(reader.getNodeName())) {
                continue;
            }
            String className = reader.getAttribute("name");
            IpResourceSet resources = IpResourceSet.parse(reader.getValue());
            reader.moveUp();
            result = result.plus(className, resources);
        }
        return result;
    }

}
