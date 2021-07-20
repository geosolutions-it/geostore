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
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Type;

/**
 * Class Group.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
@Entity(name = "UserGroup")
@Table(name = "gs_usergroup", uniqueConstraints = { @UniqueConstraint(columnNames = { "groupName" }) }, indexes = {
        @Index(name = "idx_usergroup_name", columnList = "groupName")
})
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "gs_usergroup")
@XmlRootElement(name = "UserGroup")
public class UserGroup implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 6065837305601115748L;

    /** The id. */
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, updatable = false, length = 255)
    private String groupName;

    @Column(nullable = true, updatable = true, length = 255)
    private String description;
    
    /*
     * Only To allow the CASCADING operation
     */
    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private List<SecurityRule> security;

    @Type(type="yes_no")
    @Column(nullable = false,updatable =true)
    private boolean enabled=true;
    
    private transient List<User> users = new ArrayList<User>();
    
    @XmlTransient
    public List<User> getUsers() {
        return users;
    }

    /**
     * Users belonging to this UserGroup.
     * 
     * @param users
     */
    public void setUsers(List<User> users) {
        this.users = users;
    }

    /**
     * 
     * @return the enabled flag
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * set enabled flag
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the id
     */
    //@XmlTransient
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
     * @return the groupName
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * @param groupName the groupName to set
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName()).append('[');

        if (id != null) {
            builder.append("id=").append(id);
        }

        if (groupName != null) {
            builder.append(", ");
            builder.append("groupName=").append(groupName);
        }

        builder.append(']');

        return builder.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((groupName == null) ? 0 : groupName.hashCode());
        result = (prime * result) + ((id == null) ? 0 : id.hashCode());
        result = (prime * result) + ((security == null) ? 0 : security.hashCode());

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
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

        UserGroup other = (UserGroup) obj;
        if (groupName == null) {
            if (other.groupName != null) {
                return false;
            }
        } else if (!groupName.equals(other.groupName)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
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

}
