/*
 * ====================================================================
 *
 * Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.core.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Index;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.sun.xml.bind.CycleRecoverable;

import javax.xml.bind.annotation.XmlElementWrapper;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Class Resource.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 */
@Entity(name = "Resource")
@Table(name = "gs_resource", uniqueConstraints = { @UniqueConstraint(columnNames = { "name" }) }, indexes = {
        @Index(name = "idx_resource_name", columnList = "name"),
        @Index(name = "idx_resource_description", columnList = "description"),
        @Index(name = "idx_resource_creation", columnList = "creation"),
        @Index(name = "idx_resource_update", columnList = "lastUpdate"),
        @Index(name = "idx_resource_metadata", columnList = "metadata"),
        @Index(name = "idx_resource_category", columnList = "category_id")
})
// @Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "gs_resource")
@XmlRootElement(name = "Resource")
public class Resource implements Serializable, CycleRecoverable {

    private static final long serialVersionUID = 4852100679788007328L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, updatable = true)
    private String name;

    @Column(nullable = true, updatable = true, length = 10000)
    private String description;

    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date creation;

    @Column(nullable = true, updatable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdate;

    @Column(nullable = true, updatable = true, length = 30000)
    private String metadata;

    @OneToMany(mappedBy = "resource", cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    private List<Attribute> attribute;

    @OneToOne(mappedBy = "resource", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private StoredData data;

    @ManyToOne(optional = false)
    // @ForeignKey(name = "fk_resource_category")
    private Category category;

    /*
     * Only To allow the CASCADING operation
     */
    @OneToMany(mappedBy = "resource", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private List<SecurityRule> security;

    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the creation
     */
    public Date getCreation() {
        return creation;
    }

    /**
     * @param creation the creation to set
     */
    public void setCreation(Date creation) {
        this.creation = creation;
    }

    /**
     * @return the lastUpdate
     */
    public Date getLastUpdate() {
        return lastUpdate;
    }

    /**
     * @param lastUpdate the lastUpdate to set
     */
    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    /**
     * @return the metadata
     */
    public String getMetadata() {
        return metadata;
    }

    /**
     * @param metadata the metadata to set
     */
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    /**
     * @return the attribute
     */
    @XmlElementWrapper(name = "Attributes")
    public List<Attribute> getAttribute() {
        return attribute;
    }

    /**
     * @param attribute the attribute to set
     */
    public void setAttribute(List<Attribute> attribute) {
        this.attribute = attribute;
    }

    /**
     * @return the data
     */
    // @XmlTransient
    public StoredData getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(StoredData data) {
        this.data = data;
    }

    /**
     * @return the category
     */
    public Category getCategory() {
        return category;
    }

    /**
     * @param category the category to set
     */
    public void setCategory(Category category) {
        this.category = category;
    }

    /**
     * @return the security
     */
    @XmlTransient
    public List<SecurityRule> getSecurity() {
        return security;
    }

    /**
     * @param security the security to set
     */
    public void setSecurity(List<SecurityRule> security) {
        this.security = security;
    }

    /*
     * (non-Javadoc) @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName()).append('[');

        if (name != null) {
            builder.append("name=").append(name);
        }

        if (description != null) {
            builder.append(", ");
            builder.append("description=").append(description);
        }

        if (creation != null) {
            builder.append(", ");
            builder.append("creation=").append(creation);
        }

        if (lastUpdate != null) {
            builder.append(", ");
            builder.append("lastUpdate=").append(lastUpdate);
        }

        if (metadata != null) {
            builder.append(", ");
            builder.append("metadata=").append(metadata);
        }

        if (attribute != null) {
            builder.append(", ");
            builder.append("attribute=").append(attribute.toString());
        }

        if (data != null) {
            builder.append(", ");
            builder.append("data=").append(data.toString());
        }

        if (category != null) {
            builder.append(", ");
            builder.append("category=").append(category.toString());
        }

        builder.append(']');

        return builder.toString();
    }

    /*
     * (non-Javadoc) @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((attribute == null) ? 0 : attribute.hashCode());
        result = (prime * result) + ((category == null) ? 0 : category.hashCode());
        result = (prime * result) + ((creation == null) ? 0 : creation.hashCode());
        result = (prime * result) + ((data == null) ? 0 : data.hashCode());
        result = (prime * result) + ((description == null) ? 0 : description.hashCode());
        result = (prime * result) + ((id == null) ? 0 : id.hashCode());
        result = (prime * result) + ((lastUpdate == null) ? 0 : lastUpdate.hashCode());
        result = (prime * result) + ((metadata == null) ? 0 : metadata.hashCode());
        result = (prime * result) + ((name == null) ? 0 : name.hashCode());
        result = (prime * result) + ((security == null) ? 0 : security.hashCode());

        return result;
    }

    /*
     * (non-Javadoc) @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Resource other = (Resource) obj;
        if (attribute == null) {
            if (other.attribute != null) {
                return false;
            }
        } else if (!attribute.equals(other.attribute)) {
            return false;
        }
        if (category == null) {
            if (other.category != null) {
                return false;
            }
        } else if (!category.equals(other.category)) {
            return false;
        }
        if (creation == null) {
            if (other.creation != null) {
                return false;
            }
        } else if (!creation.equals(other.creation)) {
            return false;
        }
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (lastUpdate == null) {
            if (other.lastUpdate != null) {
                return false;
            }
        } else if (!lastUpdate.equals(other.lastUpdate)) {
            return false;
        }
        if (metadata == null) {
            if (other.metadata != null) {
                return false;
            }
        } else if (!metadata.equals(other.metadata)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (security == null) {
            if (other.security != null) {
                return false;
            }
        } else if (!security.equals(other.security)) {
            return false;
        }

        return true;
    }

    /*
     * (non-Javadoc) @see com.sun.xml.bind.CycleRecoverable#onCycleDetected(com.sun.xml.bind.CycleRecoverable.Context)
     */
    @Override
    public Object onCycleDetected(Context arg0) {
        Resource r = new Resource();
        r.setCreation(this.creation);
        r.setDescription(this.description);
        r.setLastUpdate(this.lastUpdate);
        r.setMetadata(this.metadata);
        r.setName(this.name);
        r.setAttribute(null);
        r.setData(null);

        return r;
    }
}
