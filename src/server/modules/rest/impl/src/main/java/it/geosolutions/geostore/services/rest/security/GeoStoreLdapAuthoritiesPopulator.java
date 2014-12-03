/**
 * 
 */
package it.geosolutions.geostore.services.rest.security;

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
public class GeoStoreLdapAuthoritiesPopulator extends DefaultLdapAuthoritiesPopulator {

    private static final Log logger = LogFactory.getLog(GeoStoreLdapAuthoritiesPopulator.class);

    /**
     * Template that will be used for searching
     */
    private final SpringSecurityLdapTemplate ldapTemplate;

    /**
     * Controls used to determine whether group searches should be performed over the full sub-tree from the base DN. Modified by searchSubTree
     * property
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

    @Override
    public Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
        if (roleSearchBase == null && groupSearchBase == null) {
            return new HashSet<GrantedAuthority>();
        }

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();

        // Searching for ROLES
        if (roleSearchBase != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Searching for roles for user '" + username + "', DN = " + "'" + userDn
                        + "', with filter " + roleSearchFilter + " in search base '" + roleSearchBase
                        + "'");
            }

            Set<String> userRoles = ldapTemplate.searchForSingleAttributeValues(roleSearchBase,
                    roleSearchFilter, new String[] { userDn, username }, groupRoleAttribute);

            if (logger.isDebugEnabled()) {
                logger.debug("Roles from search: " + userRoles);
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
            if (logger.isDebugEnabled()) {
                logger.debug("Searching for roles for user '" + username + "', DN = " + "'" + userDn
                        + "', with filter " + groupSearchFilter + " in search base '" + groupSearchBase
                        + "'");
            }

            Set<String> userGroups = ldapTemplate.searchForSingleAttributeValues(groupSearchBase,
                    groupSearchFilter, new String[] { userDn, username }, groupRoleAttribute);

            if (logger.isDebugEnabled()) {
                logger.debug("Roles from search: " + userGroups);
            }

            for (String group : userGroups) {

                if (convertToUpperCase) {
                    group = group.toUpperCase();
                }

                authorities.add(new GrantedAuthorityImpl(group));
            }
        }

        return authorities;
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
