package net.ripe.rpki.domain.property;

import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.apache.commons.lang.Validate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name="property")
@SequenceGenerator(name = "seq_ca_property", sequenceName = "seq_all", allocationSize=1)
public class PropertyEntity extends EntitySupport {

    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ca_property")
    private Long id;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;


    protected PropertyEntity() {
    }

    public PropertyEntity(String key, String value) {
        Validate.notNull(key, "Property key is required");
        Validate.notNull(value, "Property value is required");
        this.key = key;
        this.value = value;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        Validate.notNull(value, "Property value is required");
        this.value = value;
    }
}
