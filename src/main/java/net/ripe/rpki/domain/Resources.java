package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;

public class Resources {

    public static final ImmutableResourceSet ALL_RESOURCES = ImmutableResourceSet.parse("AS0-AS4294967295, 0.0.0.0/0, ::/0");
    public static final ImmutableResourceSet ALL_AS_RESOURCES = ImmutableResourceSet.parse("AS0-AS4294967295");
    public static final ImmutableResourceSet ALL_IPV4_RESOURCES = ImmutableResourceSet.parse("0.0.0.0/0");
    public static final ImmutableResourceSet ALL_IPV6_RESOURCES = ImmutableResourceSet.parse("::/0");

    public static final String DEFAULT_RESOURCE_CLASS = "DEFAULT";
}
