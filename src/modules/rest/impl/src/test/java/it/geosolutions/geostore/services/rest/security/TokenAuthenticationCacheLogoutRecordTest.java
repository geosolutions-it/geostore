/* ====================================================================
 *
 * Copyright (C) 2026 GeoSolutions S.A.S.
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

import static org.junit.jupiter.api.Assertions.*;

import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;

/**
 * The logout record kept by {@link TokenAuthenticationCache}: it outlives the authentication of a
 * session whose access token expired, so that the logout can still end the session at the provider.
 */
class TokenAuthenticationCacheLogoutRecordTest {

    private static final String ID_TOKEN = unsignedJwt("{\"typ\":\"ID\",\"sub\":\"user\"}");

    private static String unsignedJwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
                + "."
                + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + enc.encodeToString("sig".getBytes(StandardCharsets.UTF_8));
    }

    private static Authentication session(String idToken, long expiresInMillis) {
        Instant expiresAt = Instant.now().plusMillis(expiresInMillis);
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-token",
                        expiresAt.minusSeconds(300),
                        expiresAt);
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        authentication.setDetails(new TokenDetails(accessToken, idToken, "oidcOAuth2Config"));
        return authentication;
    }

    @Test
    void recordOutlivesTheAuthenticationOfAnExpiredAccessToken() throws InterruptedException {
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry("access-token", session(ID_TOKEN, 1000));
        assertNotNull(cache.get("access-token"));

        Thread.sleep(1500);

        assertNull(cache.get("access-token"), "the authentication expires with the access token");
        TokenAuthenticationCache.LogoutRecord record = cache.getLogoutRecord("access-token");
        assertNotNull(record, "the logout record is kept for the cache lifetime");
        assertEquals("oidc", record.getProvider(), "provider name, not the bean name");
        assertEquals(ID_TOKEN, record.getIdToken());
    }

    @Test
    void removeEntryDropsTheRecordToo() {
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry("access-token", session(ID_TOKEN, 300_000));

        cache.removeEntry("access-token");

        assertNull(cache.get("access-token"));
        assertNull(cache.getLogoutRecord("access-token"));
    }

    @Test
    void noRecordWithoutAnIdToken() {
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry("access-token", session(null, 300_000));

        assertNotNull(cache.get("access-token"));
        assertNull(cache.getLogoutRecord("access-token"));
        assertNull(cache.getLogoutRecord(null));
    }

    @Test
    void recordDoesNotPrintTheIdToken() {
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry("access-token", session(ID_TOKEN, 300_000));

        String text = cache.getLogoutRecord("access-token").toString();

        assertFalse(text.contains(ID_TOKEN), text);
        assertTrue(text.contains("oidc"), text);
    }
}
