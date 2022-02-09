package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;

public class Resources {

    public static final IpResourceSet ALL_RESOURCES = IpResourceSet.parse("AS0-AS4294967295, 0.0.0.0/0, ::/0");
    public static final IpResourceSet ALL_AS_RESOURCES = IpResourceSet.parse("AS0-AS4294967295");
    public static final IpResourceSet ALL_IPV4_RESOURCES = IpResourceSet.parse("0.0.0.0/0");
    public static final IpResourceSet ALL_IPV6_RESOURCES = IpResourceSet.parse("::/0");

    public static final String DEFAULT_RESOURCE_CLASS = "DEFAULT";
}
