package it.geosolutions.geostore.services.rest.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.password.PwEncoder;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.rest.security.UserServiceAuthenticationProvider;

import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.security.SecurityContext;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Accounts that still have their former default password keep working after an upgrade, so existing
 * installations are not locked out; startup reports them instead (see GeoStoreInit).
 */
public class LegacySampleAuthenticationTest {

    private static User stored(String name, String rawPassword) {
        User user = new User();
        user.setId(1L);
        user.setName(name);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setPassword(PwEncoder.encode(rawPassword));
        return user;
    }

    @Test
    public void providerStillAuthenticatesAccountsOnTheirFormerDefaultPassword() throws Exception {
        for (String name : new String[] {"admin", "user"}) {
            UserService users = mock(UserService.class);
            when(users.get(anyString())).thenReturn(stored(name, name));
            UserServiceAuthenticationProvider provider = new UserServiceAuthenticationProvider();
            ReflectionTestUtils.setField(provider, "userService", users);

            Authentication result =
                    provider.authenticate(new UsernamePasswordAuthenticationToken(name, name));

            assertEquals(name, ((User) result.getPrincipal()).getName());
        }
    }

    @Test
    public void interceptorStillAuthenticatesAccountsOnTheirFormerDefaultPassword() {
        for (String name : new String[] {"admin", "user"}) {
            User account = stored(name, name);
            AbstractGeoStoreAuthenticationInterceptor interceptor =
                    new AbstractGeoStoreAuthenticationInterceptor() {
                        @Override
                        protected User getUser(String username, Message message) {
                            return account;
                        }
                    };
            MessageImpl message = new MessageImpl();
            AuthorizationPolicy policy = new AuthorizationPolicy();
            policy.setUserName(name);
            policy.setPassword(name);
            message.put(AuthorizationPolicy.class, policy);

            interceptor.handleMessage(message);

            assertEquals(
                    name,
                    ((GeoStorePrincipal) message.get(SecurityContext.class).getUserPrincipal())
                            .getUser()
                            .getName());
        }
    }
}
