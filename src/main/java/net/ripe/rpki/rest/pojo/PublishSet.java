package net.ripe.rpki.rest.pojo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PublishSet {

    private String ifMatch;
    private List<ROA> added = Collections.emptyList();
    private List<ROA> deleted = Collections.emptyList();

}
