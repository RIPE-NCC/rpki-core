package net.ripe.rpki.rest.pojo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PublishSet {

    private String ifMatch;
    private List<ApiRoaPrefix> added = Collections.emptyList();
    private List<ApiRoaPrefix> deleted = Collections.emptyList();

}
