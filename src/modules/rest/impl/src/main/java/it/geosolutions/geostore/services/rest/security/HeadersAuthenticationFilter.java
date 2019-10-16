/* ====================================================================
 *
 * Copyright (C) 2019 GeoSolutions S.A.S.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;

public class HeadersAuthenticationFilter extends GeoStoreAuthenticationFilter {
    public static final String DEFAULT_USERNAME_HEADER = "x-geostore-user";
    public static final String DEFAULT_GROUPS_HEADER = "x-geostore-groups";
    public static final String DEFAULT_ROLE_HEADER = "x-geostore-role";
    
    private String usernameHeader = DEFAULT_USERNAME_HEADER;
    private String groupsHeader = DEFAULT_GROUPS_HEADER;
    private String roleHeader = DEFAULT_ROLE_HEADER;
    private String listDelimiter=",";
    private String defaultRole = "USER";
    
    private GrantedAuthoritiesMapper authoritiesMapper;
        
    @Override
    protected void authenticate(HttpServletRequest req) {
        String username = req.getHeader(usernameHeader);
        
        if (username != null) {
            User user = new User();
            user.setId(-1L);
            user.setTrusted(true);
            user.setName(username);
            user.setRole(getUserRole(defaultRole));
            String groups = req.getHeader(groupsHeader);
            List<GrantedAuthority> groupAuthorities = new ArrayList<GrantedAuthority>();
            user.setGroups(new HashSet<UserGroup>());
            if (groups != null) {
                String[] groupsList = groups.split(listDelimiter);
                long groupCounter = 1;
                for (String groupName : groupsList) {
                    UserGroup group = new UserGroup();
                    group.setId(-1L);
                    group.setGroupName(groupName);
                    group.setId(groupCounter++);
                    group.setEnabled(true);
                    user.getGroups().add(group);
                    groupAuthorities.add(new GrantedAuthorityImpl(groupName));
                }
                
            }
            List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
            String role = req.getHeader(roleHeader);
            if (role != null) {
                user.setRole(getUserRole(role));
                authorities.add(createRole(role));
            } else if (authoritiesMapper != null) {
                
                for (GrantedAuthority authority : authoritiesMapper.mapAuthorities(groupAuthorities)) {
                    authorities.add(createRole(authority.getAuthority()));
                    if (role == null) {
                        Role userRole = getUserRole(authority.getAuthority());
                        if (userRole != null) {
                            user.setRole(morePrivileged(userRole, user.getRole()));
                        }
                    }
                }
            } else {
                authorities.add(createRole(user.getRole().name()));
            }
            Authentication auth =  new PreAuthenticatedAuthenticationToken(user, "", authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
    }

    private Role morePrivileged(Role role1, Role role2) {
        if (role1.equals(Role.ADMIN) || role2.equals(Role.ADMIN)) {
            return Role.ADMIN;
        }
        if (role1.equals(Role.USER) || role2.equals(Role.USER)) {
            return Role.USER;
        }
        return Role.GUEST;
    }

    private GrantedAuthorityImpl createRole(String role) {
        return new GrantedAuthorityImpl("ROLE_" + role);
    }

    private Role getUserRole(String role) {
        if ("USER".equalsIgnoreCase(role)) {
            return Role.USER;
        }
        if ("ADMIN".equalsIgnoreCase(role)) {
            return Role.ADMIN;
        }
        if ("GUEST".equalsIgnoreCase(role)) {
            return Role.GUEST;
        }
        return getUserRole(defaultRole);
    }

    public String getUsernameHeader() {
        return usernameHeader;
    }

    public void setUsernameHeader(String usernameHeader) {
        this.usernameHeader = usernameHeader;
    }

    public String getGroupsHeader() {
        return groupsHeader;
    }

    public void setGroupsHeader(String groupsHeader) {
        this.groupsHeader = groupsHeader;
    }

    public String getRoleHeader() {
        return roleHeader;
    }

    public void setRoleHeader(String roleHeader) {
        this.roleHeader = roleHeader;
    }

    public String getListDelimiter() {
        return listDelimiter;
    }

    public void setListDelimiter(String listDelimiter) {
        this.listDelimiter = listDelimiter;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public GrantedAuthoritiesMapper getAuthoritiesMapper() {
        return authoritiesMapper;
    }

    public void setAuthoritiesMapper(GrantedAuthoritiesMapper authoritiesMapper) {
        this.authoritiesMapper = authoritiesMapper;
    }
    
    
}
