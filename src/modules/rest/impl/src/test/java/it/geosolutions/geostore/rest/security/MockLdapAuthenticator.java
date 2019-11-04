package it.geosolutions.geostore.rest.security;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import it.geosolutions.geostore.core.ldap.MockDirContextOperations;

public class MockLdapAuthenticator implements LdapAuthenticator {

    @Override
    public DirContextOperations authenticate(Authentication authentication) {
        return new MockDirContextOperations();
    }

}
