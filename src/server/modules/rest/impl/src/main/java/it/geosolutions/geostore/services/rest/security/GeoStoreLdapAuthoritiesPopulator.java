/* ====================================================================
 *
 * Copyright (C) 2014 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;

import java.util.HashSet;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ldap.core.ContextSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.util.Assert;

/**
 * @author alessio.fabiani
 *
 */
public class GeoStoreLdapAuthoritiesPopulator extends
		DefaultLdapAuthoritiesPopulator {

	private static final Log logger = LogFactory.getLog(GeoStoreLdapAuthoritiesPopulator.class);
	
	/**
     * Template that will be used for searching
     */
    private final SpringSecurityLdapTemplate ldapTemplate;
    
    /**
     * Controls used to determine whether group searches should be performed over the full sub-tree from the
     * base DN. Modified by searchSubTree property
     */
    private final SearchControls searchControls = new SearchControls();

    /**
     * The ID of the attribute which contains the role name for a group
     */
    private String groupRoleAttribute = "cn";

    /**
     * The base DN from which the search for group membership should be performed
     */
    private String groupSearchBase;
    private String roleSearchBase;

    /**
     * The pattern to be used for the user search. {0} is the user's DN
     */
    private String groupSearchFilter = "(member={0})";
    private String roleSearchFilter = "(member={0})";
    /**
     * The role prefix that will be prepended to each role name
     */
    private String rolePrefix = "ROLE_";
    /**
     * Should we convert the role name to uppercase
     */
    private boolean convertToUpperCase = true;
    
    private GrantedAuthoritiesMapper authoritiesMapper = null;

    /**
     * @param contextSource
     * @param groupSearchBase
     */
    public GeoStoreLdapAuthoritiesPopulator(ContextSource contextSource, String groupSearchBase,
            String roleSearchBase) {
        super(contextSource, groupSearchBase);

        Assert.notNull(contextSource, "contextSource must not be null");
        ldapTemplate = new SpringSecurityLdapTemplate(contextSource);
        ldapTemplate.setSearchControls(searchControls);

        this.groupSearchBase = groupSearchBase;

        if (groupSearchBase == null) {
            logger.info("groupSearchBase is null. No group search will be performed.");
        } else if (groupSearchBase.length() == 0) {
            logger.info("groupSearchBase is empty. Searches will be performed from the context source base");
        }

        this.roleSearchBase = roleSearchBase;

        if (roleSearchBase == null) {
            logger.info("roleSearchBase is null. No group search will be performed.");
        } else if (roleSearchBase.length() == 0) {
            logger.info("roleSearchBase is empty. Searches will be performed from the context source base");
        }
    }

    public void setAuthoritiesMapper(GrantedAuthoritiesMapper authoritiesMapper) {
        this.authoritiesMapper = authoritiesMapper;
    }



    @Override
	public Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
        if (roleSearchBase == null && groupSearchBase == null) {
            return new HashSet<GrantedAuthority>();
        }

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();

        // Searching for ROLES
        if (logger.isDebugEnabled()) {
            logger.debug("Searching for roles for user '" + username + "', DN = " + "'" + userDn + "', with filter "
                    + roleSearchFilter + " in search base '" + roleSearchBase + "'");
        }

        String[] rolesRoots = roleSearchBase.split(",");
        for(String rolesRoot : rolesRoots) {
            addAuthorities(userDn, username, authorities, rolesRoot, roleSearchFilter, rolePrefix);
        }
        
        // Searching for Groups
        if (logger.isDebugEnabled()) {
            logger.debug("Searching for groups for user '" + username + "', DN = " + "'" + userDn + "', with filter "
                    + groupSearchFilter + " in search base '" + groupSearchBase + "'");
        }
        String[] groupsRoots = groupSearchBase.split(",");
        for(String groupsRoot : groupsRoots) {
            addAuthorities(userDn, username, authorities, groupsRoot, groupSearchFilter, null);
        }

        

        
        if(authoritiesMapper != null) {
            return new HashSet<GrantedAuthority>(authoritiesMapper.mapAuthorities(authorities));
        }
        return authorities;
    }

    private void addAuthorities(String userDn, String username, Set<GrantedAuthority> authorities,
            String root, String filter, String authorityPrefix) {
        Set<String> ldapAuthorities = ldapTemplate.searchForSingleAttributeValues(root, filter,
            new String[]{userDn, username}, groupRoleAttribute);
        if (logger.isDebugEnabled()) {
            logger.debug("Authorities from search: " + ldapAuthorities);
        }
        for (String authority : ldapAuthorities) {

            if (convertToUpperCase) {
                authority = authority.toUpperCase();
            }

            String prefix = (authorityPrefix != null && !authority.startsWith(authorityPrefix) ? authorityPrefix : "");
            authorities.add(new GrantedAuthorityImpl(prefix + authority));
        }
    }

	@Override
	public void setConvertToUpperCase(boolean convertToUpperCase) {
		super.setConvertToUpperCase(convertToUpperCase);
		this.convertToUpperCase = convertToUpperCase;
	}

	@Override
	public void setGroupRoleAttribute(String groupRoleAttribute) {
		super.setGroupRoleAttribute(groupRoleAttribute);
		this.groupRoleAttribute = groupRoleAttribute;
	}

	@Override
	public void setGroupSearchFilter(String groupSearchFilter) {
		super.setGroupSearchFilter(groupSearchFilter);
		this.groupSearchFilter = groupSearchFilter;
	}

	public void setRoleSearchFilter(String roleSearchFilter) {
		this.roleSearchFilter = roleSearchFilter;
	}
	
	@Override
	public void setRolePrefix(String rolePrefix) {
		super.setRolePrefix(rolePrefix);
		this.rolePrefix = rolePrefix;
	}

}
