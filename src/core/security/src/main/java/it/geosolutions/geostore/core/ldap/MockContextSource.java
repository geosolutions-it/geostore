package it.geosolutions.geostore.core.ldap;

import javax.naming.directory.DirContext;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.ContextSource;

public class MockContextSource implements ContextSource {

    DirContext ctx;

    public MockContextSource(DirContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public DirContext getContext(String arg0, String arg1) throws NamingException {
        return ctx;
    }

    @Override
    public DirContext getReadOnlyContext() throws NamingException {
        return ctx;
    }

    @Override
    public DirContext getReadWriteContext() throws NamingException {
        return ctx;
    }
}
