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

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.util.Assert;

import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;

/**
 * @author alessio.fabiani
 *
 */
public class GeoStoreLdapAuthoritiesPopulator extends
        DefaultLdapAuthoritiesPopulator implements GroupsRolesService {

    private static class Authority {
        private String name;

        private String dn;

        public String getName() {
            return name;
        }

        public String getDn() {
            return dn;
        }

        public Authority(String name, String dn) {
            super();
            this.name = name;
            this.dn = dn;
        }

    }

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
    
    private String allGroupsSearchFilter = "(objectClass=group)";
    private String allRolesSearchFilter = "(objectClass=group)";
    /**
     * The role prefix that will be prepended to each role name
     */
    private String rolePrefix = "ROLE_";

    private boolean searchSubtree = false;

    private boolean enableHierarchicalGroups = false;

    private String groupInGroupSearchFilter = "(member={0})";

    private int maxLevelGroupsSearch = Integer.MAX_VALUE;
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
        return getGroupsOrRoles(userDn, username, true, true);
    }

    private Set<GrantedAuthority> getGroupsOrRoles(String userDn, String username, boolean groups, boolean roles) {
        if (roleSearchBase == null && groupSearchBase == null) {
            return new HashSet<GrantedAuthority>();
        }

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();
        String[] searchParams = username == null ? new String[] {} : new String[] {userDn, username};
        if(roles) {
            // Searching for ROLES
            if (logger.isDebugEnabled()) {
                logger.debug("Searching for roles for user '" + username + "', DN = " + "'" + userDn + "', with filter "
                        + roleSearchFilter + " in search base '" + roleSearchBase + "'");
            }
    
            String[] rolesRoots = roleSearchBase.split(";");
            String filter = username == null ? allRolesSearchFilter : roleSearchFilter;
            
            for(String rolesRoot : rolesRoots) {
                addAuthorities(searchParams, authorities, rolesRoot, filter, rolePrefix, false);
            }
        }
        
        if(groups) {
            // Searching for Groups
            if (logger.isDebugEnabled()) {
                logger.debug("Searching for groups for user '" + username + "', DN = " + "'" + userDn + "', with filter "
                        + groupSearchFilter + " in search base '" + groupSearchBase + "'");
            }
            String[] groupsRoots = groupSearchBase.split(";");
            String filter = username == null ? allGroupsSearchFilter : groupSearchFilter;
            for(String groupsRoot : groupsRoots) {
                addAuthorities(searchParams, authorities, groupsRoot, filter, null, enableHierarchicalGroups);
            }
        }
                
        if(authoritiesMapper != null) {
            return new HashSet<GrantedAuthority>(authoritiesMapper.mapAuthorities(authorities));
        }
        return authorities;
    }

    public Set<GrantedAuthority> getAllGroups() {
        return getGroupsOrRoles(null, null, true, false);
    }
    
    public Set<GrantedAuthority> getAllRoles() {
        return getGroupsOrRoles(null, null, false, true);
    }
    
    private void addAuthorities(String[] params, Set<GrantedAuthority> authorities,
            String root, String filter, String authorityPrefix, boolean hierarchical) {
        addAuthorities(params, authorities, root, filter, authorityPrefix, hierarchical, 0);
    }
    
    private void addAuthorities(String[] params, Set<GrantedAuthority> authorities,
            String root, String filter, String authorityPrefix, boolean hierarchical, int level) {
        String formattedFilter = MessageFormat.format(filter, params);
        
        List ldapAuthorities = ldapTemplate.search(root, formattedFilter, new AbstractContextMapper() {
            @Override
            protected Object doMapFromContext(DirContextOperations ctx) {
                return new Authority(ctx.getStringAttribute(groupRoleAttribute), ctx.getNameInNamespace());
            }
        });

        if (logger.isDebugEnabled()) {
            logger.debug("Authorities from search: " + ldapAuthorities);
        }
        for (Object authority : ldapAuthorities) {
        	Authority ldapAuthority = (Authority)authority;
        	
            boolean added = addAuthority(authorities, authorityPrefix, ldapAuthority.getName());
            if (added && hierarchical && level < maxLevelGroupsSearch) {
                String[] searchParams = new String[] {ldapAuthority.getDn(), ldapAuthority.getName()};
                addAuthorities(searchParams, authorities, root, groupInGroupSearchFilter, authorityPrefix, hierarchical, level + 1);
            }
        }
    }

    private boolean addAuthority(Set<GrantedAuthority> authorities, String authorityPrefix,
            String authority) {
        if (convertToUpperCase) {
            authority = authority.toUpperCase();
        }

        String prefix = (authorityPrefix != null && !authority.startsWith(authorityPrefix) ? authorityPrefix : "");
        
        SimpleGrantedAuthority role = new SimpleGrantedAuthority(prefix + authority);
        if (!authorities.contains(role)) {
            authorities.add(role);
            return true;
        }
        return false;
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

	public void setSearchSubtree(boolean searchSubtree) {
		if (searchSubtree) {
        	searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        } else {
        	searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        }
        
		this.searchSubtree = searchSubtree;
	}
	
	public void setEnableHierarchicalGroups(boolean enableHierarchicalGroups) {
		this.enableHierarchicalGroups = enableHierarchicalGroups;
	}

	public void setGroupInGroupSearchFilter(String groupInGroupSearchFilter) {
		this.groupInGroupSearchFilter = groupInGroupSearchFilter;
	}

	public void setMaxLevelGroupsSearch(int maxLevelGroupsSearch) {
		this.maxLevelGroupsSearch = maxLevelGroupsSearch;
	}

}
