package net.ripe.rpki.rest.pojo;

import lombok.Data;

import java.util.List;

@Data
public class ResourcesCollection {
    private final List<String> resources;
}
