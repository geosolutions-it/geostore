package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.model.enums.Role;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanNameAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base configuration class for authentication mechanisms that rely on external Identity providers.
 */
public abstract class IdPConfiguration implements BeanNameAware {

    protected String beanName;

    protected boolean enabled = false;

    protected boolean autoCreateUser;

    protected String internalRedirectUri;

    protected String redirectUri;

    protected Role authenticatedDefaultRole;

    protected List<String> defaultGroups = Collections.emptyList();

    protected boolean forceSecureCookies = false;

    /** Default lifetime of the refresh token cookie, in seconds (7 days). */
    public static final int DEFAULT_REFRESH_TOKEN_COOKIE_MAX_AGE = 604800;

    /** Default path of the refresh token cookie. */
    public static final String DEFAULT_REFRESH_TOKEN_COOKIE_PATH = "/";

    protected int refreshTokenCookieMaxAge = DEFAULT_REFRESH_TOKEN_COOKIE_MAX_AGE;

    protected String refreshTokenCookiePath = DEFAULT_REFRESH_TOKEN_COOKIE_PATH;

    /**
     * @return true if the filter to which this configuration object refers is enabled. False
     *     otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the enabled flag.
     *
     * @param enabled true to enable the filter to which this configuration refers.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return true if the logged-in user should be created on the db if not present. False
     *     otherwise.
     */
    public boolean isAutoCreateUser() {
        return autoCreateUser;
    }

    /**
     * Set the autocreate user flag.
     *
     * @param autoCreateUser the autoCreateUser flag.
     */
    public void setAutoCreateUser(Boolean autoCreateUser) {
        this.autoCreateUser = autoCreateUser;
    }

    public void setAutoCreateUser(boolean autoCreateUser) {
        this.autoCreateUser = autoCreateUser;
    }

    /**
     * @return true if the cookies set at login always carry the {@code Secure} attribute. False if
     *     they carry it only when the request reached the application over HTTPS (the default).
     */
    public boolean isForceSecureCookies() {
        return forceSecureCookies;
    }

    /**
     * Set whether the cookies set at login always carry the {@code Secure} attribute, e.g. when
     * users reach the application over HTTPS through a reverse proxy that talks plain HTTP to the
     * servlet container.
     *
     * @param forceSecureCookies true to always set {@code Secure}.
     */
    public void setForceSecureCookies(boolean forceSecureCookies) {
        this.forceSecureCookies = forceSecureCookies;
    }

    /**
     * @return the lifetime of the refresh token cookie in seconds, or -1 for a session cookie,
     *     which the browser drops when it closes. 7 days by default.
     */
    public int getRefreshTokenCookieMaxAge() {
        return refreshTokenCookieMaxAge;
    }

    /**
     * Set the lifetime of the refresh token cookie.
     *
     * @param refreshTokenCookieMaxAge the lifetime in seconds, or -1 for a session cookie.
     */
    public void setRefreshTokenCookieMaxAge(int refreshTokenCookieMaxAge) {
        this.refreshTokenCookieMaxAge = refreshTokenCookieMaxAge;
    }

    /**
     * @return the path of the refresh token cookie, {@code /} by default.
     */
    public String getRefreshTokenCookiePath() {
        return refreshTokenCookiePath;
    }

    /**
     * Set the path of the refresh token cookie, e.g. {@code /geostore/rest} to send it only to the
     * GeoStore REST API instead of to every application on the same host. The path must cover both
     * the login callback and the refresh endpoint.
     *
     * @param refreshTokenCookiePath the cookie path, starting with {@code /}.
     */
    public void setRefreshTokenCookiePath(String refreshTokenCookiePath) {
        this.refreshTokenCookiePath = refreshTokenCookiePath;
    }

    /**
     * Return the bean name of this configuration object.
     *
     * @return the bean name.
     */
    public String getBeanName() {
        return beanName;
    }

    @Override
    public void setBeanName(String s) {
        this.beanName = s;
    }

    /**
     * @return The internal redirect uri: the endpoint to which the client is redirected after the
     *     callback endpoint is invoked.
     */
    public String getInternalRedirectUri() {
        return internalRedirectUri;
    }

    /**
     * Set the internalRedirectUri.
     *
     * @param internalRedirectUri the internal redirect URI.
     */
    public void setInternalRedirectUri(String internalRedirectUri) {
        this.internalRedirectUri = internalRedirectUri;
    }

    /**
     * @return the redirect URI.
     */
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * Set the redirect URI.
     *
     * @param redirectUri the redirect URI.
     */
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Role getAuthenticatedDefaultRole() {
        if (authenticatedDefaultRole == null) return Role.USER;
        return authenticatedDefaultRole;
    }

    public void setAuthenticatedDefaultRole(String authenticatedDefaultRole) {
        if (StringUtils.isNotBlank(authenticatedDefaultRole))
            this.authenticatedDefaultRole = Role.valueOf(authenticatedDefaultRole);
    }

    /**
     * Optional groups always assigned to the authenticated user, in addition to the ones derived
     * from the IdP claims. They are not subject to groupMappings/dropUnmapped and are created on
     * the fly when they do not exist.
     *
     * @return the configured default group names; never null, empty when not configured.
     */
    public List<String> getDefaultGroups() {
        return defaultGroups;
    }

    /**
     * @param defaultGroups comma-separated list of group names; blank entries are discarded.
     */
    public void setDefaultGroups(String defaultGroups) {
        if (StringUtils.isBlank(defaultGroups)) {
            this.defaultGroups = Collections.emptyList();
            return;
        }
        List<String> parsed = new ArrayList<>();
        for (String name : defaultGroups.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        this.defaultGroups = parsed;
    }
}
