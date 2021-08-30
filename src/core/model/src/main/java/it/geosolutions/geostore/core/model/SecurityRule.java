/* ====================================================================
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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.ForeignKey;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Class Security.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
@Entity(name = "Security")
@Table(name = "gs_security", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "resource_id" }),
        /* @UniqueConstraint(columnNames = {"user_id", "category_id"}), */
        @UniqueConstraint(columnNames = { "resource_id", "group_id" }) /*
                                                                        * ,
                                                                        * 
                                                                        * @UniqueConstraint(columnNames = {"category_id", "group_id"})
                                                                        */
        }, indexes = {
                @Index(name = "idx_security_resource", columnList = "resource_id"),
                @Index(name = "idx_security_user", columnList = "user_id"),
                @Index(name = "idx_security_group", columnList = "group_id"),
                @Index(name = "idx_security_read", columnList = "canread"),
                @Index(name = "idx_security_write", columnList = "canwrite"),
                @Index(name = "idx_security_username", columnList = "username"),
                @Index(name = "idx_security_groupname", columnList = "groupname")
        })
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "gs_security")
@XmlRootElement(name = "Security")
public class SecurityRule implements Serializable {

    private static final long serialVersionUID = -4160546863296343389L;

    @Id
    @GeneratedValue
    private Long id;

    /**
     * A SecurityRule may refer either to a resource or to a Category, then neither of them are mandatory. A check to ensure they are not both null is
     * done in onPreUpdate() <BR>
     * TODO: it would be nice to have a DB constraint on nonnullability on them.
     */
    @ManyToOne(optional = true)
    @JoinColumn(foreignKey=@ForeignKey(name="fk_security_resource"))
    private Resource resource;

    // /**
    // * A SecurityRule may refer either to a resource or to a Category, then neither of them are mandatory. A check to ensure they
    // * are not both null is done in onPreUpdate() <BR>TODO: it would be nice to have a DB constraint on nonnullability on them.
    // */
    // @ManyToOne(optional = true)
    // @Index(name = "idx_security_category")
    // @ForeignKey(name = "fk_security_category")
    // private Category category;

    @ManyToOne(optional = true)
    @JoinColumn(foreignKey=@ForeignKey(name="fk_security_user"))
    private User user;

    @ManyToOne(optional = true)
    @JoinColumn(foreignKey=@ForeignKey(name="fk_security_group"))
    private UserGroup group;

    @Column(nullable = false, updatable = true)
    private boolean canRead;

    @Column(nullable = false, updatable = true)
    private boolean canWrite;
    
    @Column(nullable = true, updatable = true)
    private String username;
    
    @Column(nullable = true, updatable = true)
    private String groupname;

    /**
     * @throws Exception
     */
    @PreUpdate
    @PrePersist
    public void onPreUpdate() throws Exception {
        // if ( !((this.resource != null) ^ (this.category != null)) ) {
        // throw new Exception("Only one between Category and Resource can be not-null inside the Security entity");
        // }
    }

    /**
     * @return the id
     */
    @XmlTransient
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
     * @return the resource
     */
    @XmlTransient
    public Resource getResource() {
        return resource;
    }

    /**
     * @param resource the resource to set
     */
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // /**
    // * @return the category
    // */
    // @XmlTransient
    // public Category getCategory() {
    // return category;
    // }
    //
    // /**
    // * @param category the category to set
    // */
    // public void setCategory(Category category) {
    // this.category = category;
    // }

    /**
     * @return the user
     */
    @XmlTransient
    public User getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return the group
     */
    @XmlTransient
    public UserGroup getGroup() {
        return group;
    }

    /**
     * @param group the group to set
     */
    public void setGroup(UserGroup group) {
        this.group = group;
    }

    /**
     * 
     * @return the username (from external authentication)
     */
    public String getUsername() {
        return username;
    }

    /**
     * 
     * @param username the user name to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 
     * @return the group name (from external authentication)
     */
    public String getGroupname() {
        return groupname;
    }

    /**
     * 
     * @param groupname the group name to set
     */
    public void setGroupname(String groupname) {
        this.groupname = groupname;
    }

    /**
     * @return the canRead
     */
    @XmlTransient
    public boolean isCanRead() {
        return canRead;
    }

    /**
     * @param canRead the canRead to set
     */
    public void setCanRead(boolean canRead) {
        this.canRead = canRead;
    }

    /**
     * @return the canWrite
     */
    @XmlTransient
    public boolean isCanWrite() {
        return canWrite;
    }

    /**
     * @param canWrite the canWrite to set
     */
    public void setCanWrite(boolean canWrite) {
        this.canWrite = canWrite;
    }

    /*
     * (non-Javadoc) @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName()).append('[');

        if (id != null) {
            builder.append("id=").append(id);
        }

        builder.append(", ");
        builder.append("canRead=").append(canRead);

        builder.append(", ");
        builder.append("canWrite=").append(canWrite);

        if (resource != null) {
            builder.append(", ");
            builder.append("resource=").append(resource);
        }

        if (group != null) {
            builder.append(", ");
            builder.append("group=").append(group);
        }

        // if ( category != null ) {
        // builder.append(", ");
        // builder.append("category=").append(category);
        // }

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
        result = (prime * result) + (canRead ? 1231 : 1237);
        result = (prime * result) + (canWrite ? 1231 : 1237);
        // result = (prime * result)
        // + ((category == null) ? 0 : category.hashCode());
        result = (prime * result) + ((group == null) ? 0 : group.hashCode());
        result = (prime * result) + ((id == null) ? 0 : id.hashCode());
        result = (prime * result) + ((resource == null) ? 0 : resource.hashCode());
        result = (prime * result) + ((user == null) ? 0 : user.hashCode());

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

        SecurityRule other = (SecurityRule) obj;
        if (canRead != other.canRead) {
            return false;
        }
        if (canWrite != other.canWrite) {
            return false;
        }
        // if ( category == null ) {
        // if ( other.category != null ) {
        // return false;
        // }
        // } else if ( !category.equals(other.category) ) {
        // return false;
        // }
        if (group == null) {
            if (other.group != null) {
                return false;
            }
        } else if (!group.equals(other.group)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (resource == null) {
            if (other.resource != null) {
                return false;
            }
        } else if (!resource.equals(other.resource)) {
            return false;
        }
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }

        return true;
    }
}
