package net.ripe.rpki.rest.pojo;

import java.util.Collections;
import java.util.List;

public class PublishSet {
    private List<ROA> added = Collections.emptyList();
    private List<ROA> deleted = Collections.emptyList();

    public PublishSet() {
    }

    public PublishSet(List<ROA> added, List<ROA> deleted) {
        this.added = added;
        this.deleted = deleted;
    }

    public List<ROA> getAdded() {
        return added;
    }

    public void setAdded(List<ROA> added) {
        this.added = added;
    }

    public List<ROA> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<ROA> deleted) {
        this.deleted = deleted;
    }
}
