package it.geosolutions.geostore.rest.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

public class MockLdapAuthoritiesPopulator implements LdapAuthoritiesPopulator {

    @Override
    public Collection<GrantedAuthority> getGrantedAuthorities(DirContextOperations userData,
            String username) {
        return Collections.EMPTY_LIST;
    }

}
