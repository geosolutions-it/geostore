package it.geosolutions.geostore.rest.security;

import it.geosolutions.geostore.services.rest.security.GroupsRolesService;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

public class MockLdapAuthoritiesPopulator implements LdapAuthoritiesPopulator, GroupsRolesService {

    @Override
    public Collection<GrantedAuthority> getGrantedAuthorities(DirContextOperations userData,
            String username) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Set<GrantedAuthority> getAllGroups() {
        return Collections.EMPTY_SET;
    }

    @Override
    public Set<GrantedAuthority> getAllRoles() {
        return Collections.EMPTY_SET;
    }
    
    

}
