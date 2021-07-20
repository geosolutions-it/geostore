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

import it.geosolutions.geostore.core.model.enums.Role;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;

/**
 * Class User.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 */
@Entity(name = "User")
@Table(name = "gs_user", uniqueConstraints = { @UniqueConstraint(columnNames = { "name" }) }, indexes = {
        @Index(name = "idx_user_name", columnList = "name"),
        @Index(name = "idx_user_password", columnList = "user_password"),
        @Index(name = "idx_user_role", columnList = "user_role")
})
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "gs_user")
@XmlRootElement(name = "User")
public class User implements Serializable {

    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = -138056245004697133L;

    /**
     * The id.
     */
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, updatable = false, length = 255)
    private String name;

    @Column(name = "user_password", updatable = true)
    private String password;

    @Column(name = "user_role", nullable = false, updatable = true)
    @Enumerated(EnumType.STRING)
    private Role role;

    /*
     * NOT to be saved on DB
     */
    private transient String newPassword = null;
    private transient boolean trusted = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<UserAttribute> attribute;

    /*
     * Only To allow the CASCADING operation
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private List<SecurityRule> security;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "gs_usergroup_members", 
        joinColumns = { @JoinColumn(name = "user_id", nullable = false, updatable = false) },
        inverseJoinColumns = { @JoinColumn(name = "group_id", nullable = false, updatable = false) })
    private Set<UserGroup> groups;

    @Type(type="yes_no")
    @Column(nullable = false,updatable =true)
	private boolean enabled=true;

    /**
     * @return the id
     */
    // @XmlTransient
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
     * @return the group
     */
    @XmlElementWrapper
    @XmlElement(name="group")
    public Set<UserGroup> getGroups() {
        return groups;
    }

    /**
     * @param group the group to set
     */
    public void setGroups(Set<UserGroup> groups) {
        this.groups = groups;
    }

    /**
     * @return true if the group with id groupId was associated with the user and was removed.
     */
    public boolean removeGroup(long groupId) {
        if(groups != null) {
            for (UserGroup group : groups) {
                if(groupId == group.getId()) {
                    return groups.remove(group);
                }
            }
        }
        
        return false;
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
     * @return the password
     */
    @XmlTransient
    public String getPassword() {
        return password;
    }

    /**
     * <STRONG>DON'T USE THIS METHOD</STRONG> <BR>
     * You will probably break the password by using this method. <BR>
     * Please use the {@link setNewPassword()} method instead.
     * 
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the newPassword
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * Set the cleartext password. <BR>
     * Before being persisted, the password will be automatically encoded, and will be accessible with {@link getPassword()}.
     * <P>
     * Please note that this is NOT a persisted field
     * 
     * @param newPassword the cleartext newPassword
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * @return the attribute
     */
    public List<UserAttribute> getAttribute() {
        return attribute;
    }

    /**
     * @param attribute the attribute to set
     */
    public void setAttribute(List<UserAttribute> attribute) {
        this.attribute = attribute;
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
     * @return the role
     */
    public Role getRole() {
        return role;
    }

    /**
     * @param role the role to set
     */
    public void setRole(Role role) {
        this.role = role;
    }
    
    /**
     * A trusted user is not validated through the database.
     * Used when we want to externalize user authentication and we get the user from the external source.
     * 
     * @return
     */
    @XmlTransient
    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
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

        if (name != null) {
            builder.append(", ");
            builder.append("name=").append(name);
        }

        if (groups != null) {
            builder.append(", ");
            builder.append("group=").append(groups.toString());
        }

        if (role != null) {
            builder.append(", ");
            builder.append("role=").append(role);
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
        //result = (prime * result) + ((groups == null) ? 0 : groups.hashCode());
        result = (prime * result) + ((id == null) ? 0 : id.hashCode());
        result = (prime * result) + ((name == null) ? 0 : name.hashCode());
        result = (prime * result) + ((password == null) ? 0 : password.hashCode());
        result = (prime * result) + ((role == null) ? 0 : role.hashCode());
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

        User other = (User) obj;
        if (attribute == null) {
            if (other.attribute != null) {
                return false;
            }
        } else if (!attribute.equals(other.attribute)) {
            return false;
        }
        if (groups == null) {
            if (other.groups != null) {
                return false;
            }
        } else if (!groups.equals(other.groups)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (password == null) {
            if (other.password != null) {
                return false;
            }
        } else if (!password.equals(other.password)) {
            return false;
        }
        if (role != other.role) {
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
