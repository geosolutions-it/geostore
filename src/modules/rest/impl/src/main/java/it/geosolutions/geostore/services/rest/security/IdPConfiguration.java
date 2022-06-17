package it.geosolutions.geostore.services.rest.security;

import org.springframework.beans.factory.BeanNameAware;

/**
 * Base configuration class for authentication mechanisms that rely on external Identity providers.
 */
public abstract class IdPConfiguration implements BeanNameAware {

    protected String beanName;

    protected boolean enabled=false;

    protected boolean autoCreateUser;

    protected String internalRedirectUri;

    protected String redirectUri;




    /**
     * @return true if the filter to which this configuration object refers is enabled. False otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the enabled flag.
     * @param enabled true to enable the filter to which this configuration refers.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    /**
     *
     * @return true if the logged in user should be created on the db if not present.
     * False otherwise.
     */
    public boolean isAutoCreateUser() {
        return autoCreateUser;
    }

    /**
     * Set the autocreate user flag.
     * @param autoCreateUser the autoCreateUser flag.
     */
    public void setAutoCreateUser(Boolean autoCreateUser) {
        this.autoCreateUser = autoCreateUser;
    }

    @Override
    public void setBeanName(String s) {
        this.beanName=s;
    }

    /**
     * Return the bean name of this configuration object.
     * @return the bean name.
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * @return The internal redirect uri: the endpoint to which the client is redirected after the
     * callback endpoint is invoked.
     */
    public String getInternalRedirectUri() {
        return internalRedirectUri;
    }

    /**
     * Set the internalRedirectUri.
     * @param internalRedirectUri the internal redirect URI.
     */
    public void setInternalRedirectUri(String internalRedirectUri) {
        this.internalRedirectUri = internalRedirectUri;
    }


    /**
     *
     * @return the redirect URI.
     */
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * Set the redirect URI.
     * @param redirectUri the redirect URI.
     */
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}
