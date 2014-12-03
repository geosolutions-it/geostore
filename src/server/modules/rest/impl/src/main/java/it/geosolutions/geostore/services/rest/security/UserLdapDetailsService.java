/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.util.Assert;

/**
 * @author alessio.fabiani
 *
 */
public class UserLdapDetailsService implements UserDetailsService {

    private final static Logger LOGGER = Logger.getLogger(UserLdapDetailsService.class);
    
    /**
     * Template that will be used for searching
     */
    private final SpringSecurityLdapTemplate ldapTemplate;
    
    /**
     * Controls used to determine whether group searches should be performed over the full sub-tree from the base DN. Modified by searchSubTree
     * property
     */
    private final SearchControls searchControls = new SearchControls();
    
    @Autowired
    UserService userService;
    
    @Autowired
    UserGroupService userGroupService;

    /**
     * LDAP Base DN
     */
    private String baseDn = "";
    
    /**
     * The ID of the attribute which contains the role name for a group
     */
    private String userNameAttribute = "uid";
    private String userGivenNameAttribute = "givenName";
    private String userMailAttribute = "mail";
    private String userPasswordAttribute = "userPassword";

    private String groupRoleAttribute = "cn";

    /**
     * The base DN from which the search for group membership should be performed
     */
    private String userSearchBase;
    
    private String groupSearchBase;

    private String roleSearchBase;

    /**
     * The pattern to be used for the user search. {0} is the user's DN
     */
    private String userSearchFilter = "(uid={0})";
    
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
    
    public UserLdapDetailsService(ContextSource contextSource, String userSearchBase,
            String groupSearchBase,
            String roleSearchBase) {
        
        Assert.notNull(contextSource, "contextSource must not be null");
        ldapTemplate = new SpringSecurityLdapTemplate(contextSource);
        ldapTemplate.setSearchControls(searchControls);

        this.userSearchBase = userSearchBase;

        if (userSearchBase == null) {
            LOGGER.info("userSearchBase is null. No user search will be performed.");
        } else if (userSearchBase.length() == 0) {
            LOGGER.info("userSearchBase is empty. Searches will be performed from the context source base");
        }

        this.groupSearchBase = groupSearchBase;

        if (groupSearchBase == null) {
            LOGGER.info("groupSearchBase is null. No group search will be performed.");
        } else if (groupSearchBase.length() == 0) {
            LOGGER.info("groupSearchBase is empty. Searches will be performed from the context source base");
        }

        this.roleSearchBase = roleSearchBase;

        if (roleSearchBase == null) {
            LOGGER.info("roleSearchBase is null. No role search will be performed.");
        } else if (roleSearchBase.length() == 0) {
            LOGGER.info("roleSearchBase is empty. Searches will be performed from the context source base");
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException, DataAccessException
    {
        LOGGER.debug("Getting access details from LDAP !!");
        
        
        // Searching for USER
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Searching for USER '" + username + "'"
                    + " with filter " + userSearchFilter + " in search base '" + userSearchBase
                    + "'");
        }

        UserAttributesMapper userMapper = new UserAttributesMapper(userNameAttribute, userGivenNameAttribute, userMailAttribute, userPasswordAttribute);
        List<UserAttributesMapper> ldapUsers = ldapTemplate.search(userSearchBase, userSearchFilter.replace("{0}", username), userMapper);
        
//        Set<String> users = ldapTemplate.searchForSingleAttributeValues(userSearchBase,
//                userSearchFilter, new String[] { username }, userNameAttribute);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Users from search: " + ldapUsers);
        }
        
        if (ldapUsers == null || ldapUsers.isEmpty()) {
            
            UserDetails guest = new User(username, "password", true, true, true, true, 
                    new GrantedAuthority[]{ new GrantedAuthorityImpl("ANONYMOUS") });

            return guest;
        }

        // Retrieve ROLES and GROUPS
        final Map<String, Object> ldapUser = (Map<String, Object>) ldapUsers.get(0);
        
        final String userDn = userNameAttribute + "=" + username + "," + userSearchBase + "," + baseDn;

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();

        if (roleSearchBase == null && groupSearchBase == null) {
            authorities = new HashSet<GrantedAuthority>();
        }

        // Searching for ROLES
        if (roleSearchBase != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Searching for roles for user '" + username + "', "
                        + "with filter " + roleSearchFilter + " in search base '" + roleSearchBase + "'");
            }

            Set<String> userRoles = ldapTemplate.searchForSingleAttributeValues(roleSearchBase,
                    roleSearchFilter, new String[] { userDn, username }, groupRoleAttribute);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Roles from search: " + userRoles);
            }

            for (String role : userRoles) {

                if (convertToUpperCase) {
                    role = role.toUpperCase();
                }

                String prefix = (rolePrefix != null && !role.startsWith(rolePrefix) ? rolePrefix : "");
                authorities.add(new GrantedAuthorityImpl(prefix + role));
            }
        }
        
        // Searching for Groups
        if (groupSearchBase != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Searching for roles for user '" + username + "', "
                        + "with filter " + groupSearchFilter + " in search base '" + groupSearchBase + "'");
            }

            Set<String> userGroups = ldapTemplate.searchForSingleAttributeValues(groupSearchBase,
                    groupSearchFilter, new String[] { userDn, username }, groupRoleAttribute);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Roles from search: " + userGroups);
            }

            for (String group : userGroups) {

                if (convertToUpperCase) {
                    group = group.toUpperCase();
                }

                authorities.add(new GrantedAuthorityImpl(group));
            }
        }
        
        if (authorities.isEmpty()) {
            authorities.add(new GrantedAuthorityImpl("ANONYMOUS"));
        }
        
        UserDetails user = new User((String) ldapUser.get("uid"), (String) ldapUser.get("password"), 
                true, true, true, true, 
                authorities.toArray(new GrantedAuthority[authorities.size()]));
        
        return user;
    }

    /**
     * @return the baseDn
     */
    public String getBaseDn() {
        return baseDn;
    }

    /**
     * @param baseDn the baseDn to set
     */
    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    /**
     * @return the userNameAttribute
     */
    public String getUserNameAttribute() {
        return userNameAttribute;
    }

    /**
     * @param userNameAttribute the userNameAttribute to set
     */
    public void setUserNameAttribute(String userNameAttribute) {
        this.userNameAttribute = userNameAttribute;
    }

    /**
     * @return the userGivenNameAttribute
     */
    public String getUserGivenNameAttribute() {
        return userGivenNameAttribute;
    }

    /**
     * @param userGivenNameAttribute the userGivenNameAttribute to set
     */
    public void setUserGivenNameAttribute(String userGivenNameAttribute) {
        this.userGivenNameAttribute = userGivenNameAttribute;
    }

    /**
     * @return the userMailAttribute
     */
    public String getUserMailAttribute() {
        return userMailAttribute;
    }

    /**
     * @param userMailAttribute the userMailAttribute to set
     */
    public void setUserMailAttribute(String userMailAttribute) {
        this.userMailAttribute = userMailAttribute;
    }

    /**
     * @return the userPasswordAttribute
     */
    public String getUserPasswordAttribute() {
        return userPasswordAttribute;
    }

    /**
     * @param userPasswordAttribute the userPasswordAttribute to set
     */
    public void setUserPasswordAttribute(String userPasswordAttribute) {
        this.userPasswordAttribute = userPasswordAttribute;
    }

    /**
     * @return the groupRoleAttribute
     */
    public String getGroupRoleAttribute() {
        return groupRoleAttribute;
    }

    /**
     * @param groupRoleAttribute the groupRoleAttribute to set
     */
    public void setGroupRoleAttribute(String groupRoleAttribute) {
        this.groupRoleAttribute = groupRoleAttribute;
    }

    /**
     * @return the userSearchFilter
     */
    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    /**
     * @param userSearchFilter the userSearchFilter to set
     */
    public void setUserSearchFilter(String userSearchFilter) {
        this.userSearchFilter = userSearchFilter;
    }

    /**
     * @return the groupSearchFilter
     */
    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    /**
     * @param groupSearchFilter the groupSearchFilter to set
     */
    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
    }

    /**
     * @return the roleSearchFilter
     */
    public String getRoleSearchFilter() {
        return roleSearchFilter;
    }

    /**
     * @param roleSearchFilter the roleSearchFilter to set
     */
    public void setRoleSearchFilter(String roleSearchFilter) {
        this.roleSearchFilter = roleSearchFilter;
    }

    /**
     * @return the rolePrefix
     */
    public String getRolePrefix() {
        return rolePrefix;
    }

    /**
     * @param rolePrefix the rolePrefix to set
     */
    public void setRolePrefix(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    /**
     * @return the convertToUpperCase
     */
    public boolean isConvertToUpperCase() {
        return convertToUpperCase;
    }

    /**
     * @param convertToUpperCase the convertToUpperCase to set
     */
    public void setConvertToUpperCase(boolean convertToUpperCase) {
        this.convertToUpperCase = convertToUpperCase;
    }

}


class UserAttributesMapper implements AttributesMapper {

    private String userNameAttribute = "uid";
    private String userGivenNameAttribute = "givenName";
    private String userMailAttribute = "mail";
    private String userPasswordAttribute = "userPassword";
    
    /**
     * @param userNameAttribute
     * @param userGivenNameAttribute
     * @param userMailAttribute
     * @param userPasswordAttribute
     */
    public UserAttributesMapper(String userNameAttribute, String userGivenNameAttribute,
            String userMailAttribute, String userPasswordAttribute) {
        this.userNameAttribute = userNameAttribute;
        this.userGivenNameAttribute = userGivenNameAttribute;
        this.userMailAttribute = userMailAttribute;
        this.userPasswordAttribute = userPasswordAttribute;
    }

    @Override
    public Object mapFromAttributes(Attributes attributes) throws NamingException {
        Map<String, Object> map = new HashMap<String, Object>();
        String uid = (String) attributes.get(userNameAttribute).get();
        String fullname = (String) attributes.get(userGivenNameAttribute).get();
        String email = (String) attributes.get(userMailAttribute).get();
        Object password = attributes.get(userPasswordAttribute).get();
        byte[] bytes = (byte[]) password;
        String strPwd = new String(bytes);

        map.put("uid", uid);
        map.put("fullname", fullname);
        map.put("email", email);
        map.put("password", strPwd);
        return map;
    }

    /**
     * @return the userNameAttribute
     */
    public String getUserNameAttribute() {
        return userNameAttribute;
    }

    /**
     * @param userNameAttribute the userNameAttribute to set
     */
    public void setUserNameAttribute(String userNameAttribute) {
        this.userNameAttribute = userNameAttribute;
    }

    /**
     * @return the userGivenNameAttribute
     */
    public String getUserGivenNameAttribute() {
        return userGivenNameAttribute;
    }

    /**
     * @param userGivenNameAttribute the userGivenNameAttribute to set
     */
    public void setUserGivenNameAttribute(String userGivenNameAttribute) {
        this.userGivenNameAttribute = userGivenNameAttribute;
    }

    /**
     * @return the userMailAttribute
     */
    public String getUserMailAttribute() {
        return userMailAttribute;
    }

    /**
     * @param userMailAttribute the userMailAttribute to set
     */
    public void setUserMailAttribute(String userMailAttribute) {
        this.userMailAttribute = userMailAttribute;
    }

    /**
     * @return the userPasswordAttribute
     */
    public String getUserPasswordAttribute() {
        return userPasswordAttribute;
    }

    /**
     * @param userPasswordAttribute the userPasswordAttribute to set
     */
    public void setUserPasswordAttribute(String userPasswordAttribute) {
        this.userPasswordAttribute = userPasswordAttribute;
    }

}