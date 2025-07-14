package it.geosolutions.geostore.core.model;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;

@Entity(name = "IPRange")
@Table(
        name = "gs_ip_range",
        indexes = {@Index(name = "idx_ip_range_cidr", columnList = "cidr", unique = true)})
@XmlRootElement(name = "IPRange")
public class IPRange implements Serializable {

    @Id @GeneratedValue private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String cidr;

    @Column(nullable = true, length = 255)
    private String description;

    // Getters and setters...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    // equals/hashCode/toString...

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IPRange)) return false;
        IPRange ipRange = (IPRange) o;
        return Objects.equals(getId(), ipRange.getId())
                && Objects.equals(getCidr(), ipRange.getCidr())
                && Objects.equals(getDescription(), ipRange.getDescription());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCidr(), getDescription());
    }

    @Override
    public String toString() {
        return "IPRange{"
                + "id="
                + id
                + ", cidr='"
                + cidr
                + '\''
                + ", description='"
                + description
                + '\''
                + '}';
    }
}
