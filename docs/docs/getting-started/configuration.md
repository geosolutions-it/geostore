# Configuration

!!! note "Placeholder"
    This page is a placeholder. Content will be added in a future update.

## Configuration Files

GeoStore uses Spring-based configuration with property overrides:

| File | Purpose |
|------|---------|
| `geostore-ovr.properties` | Main property overrides (OAuth2, LDAP, sessions) |
| `geostore-datasource-ovr.properties` | Database connection settings |
| `geostore-spring-security.xml` | Spring Security bean configuration |

## Property Override Mechanism

Properties in `geostore-ovr.properties` override Spring bean properties using the `{beanName}.{property}` convention. For example:

```properties
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=my-client-id
```

See the [Security Property Reference](../security/properties-reference.md) for a complete list of configurable properties.
