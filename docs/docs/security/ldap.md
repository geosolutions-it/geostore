# LDAP Configuration

## Overview

GeoStore supports LDAP authentication via Spring Security's LDAP module. This allows users stored
in an external LDAP directory (such as OpenLDAP, Active Directory, or 389 Directory Server) to
authenticate against GeoStore using their LDAP credentials.

Configuration is done through two files:

- **`geostore-spring-security.xml`** -- Spring XML bean definitions for the LDAP context source,
  authentication provider, and authorities populator.
- **`geostore-ovr.properties`** -- property overrides for LDAP connection settings and provider
  behavior.

Key capabilities include:

- Bind-based LDAP authentication (verifies credentials by binding as the user)
- Separate search bases for **groups** and **roles**
- Hierarchical (nested) group resolution
- Automatic user provisioning in GeoStore upon first LDAP login
- Case-insensitive username normalization
- Optional read-only LDAP DAOs for user and group listing

---

## LDAP Properties

Set the following properties in `geostore-ovr.properties` to configure the LDAP connection and
provider behavior:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ldap.host` | String | -- | LDAP server hostname (e.g., `ldap.example.com`) |
| `ldap.port` | int | -- | LDAP server port (typically `389` for LDAP or `636` for LDAPS) |
| `ldap.root` | String | -- | LDAP root DN (e.g., `dc=example,dc=com`) |
| `geostoreLdapProvider.ignoreUsernameCase` | boolean | `false` | When `true`, normalizes LDAP usernames to uppercase to ensure a single identity per user regardless of case |

!!! note
    The `ldap.host`, `ldap.port`, and `ldap.root` properties are referenced by the `contextSource`
    bean via Spring property placeholders (`${ldap.host}`, etc.). They must be defined before the
    application context initializes.

---

## Spring XML Configuration

The LDAP beans are defined in `geostore-spring-security.xml`. The sections below describe each
key bean and its role.

### Context Source

The `contextSource` bean establishes the connection to the LDAP server. It uses Spring Security's
`DefaultSpringSecurityContextSource` with a URL constructed from the property placeholders:

```xml
<bean id="contextSource"
      class="org.springframework.security.ldap.DefaultSpringSecurityContextSource">
    <constructor-arg value="ldap://${ldap.host}:${ldap.port}/${ldap.root}" />
</bean>
```

!!! tip
    For LDAPS (LDAP over TLS), change the URL scheme to `ldaps://` and set the port to `636`
    (or your server's TLS port). You may also need to import the server certificate into the
    JVM truststore.

### Authentication Provider

The `geostoreLdapProvider` bean is the central LDAP authentication provider. It extends Spring
Security's `LdapAuthenticationProvider` via the GeoStore-specific class
`UserLdapAuthenticationProvider`, which adds:

- Automatic creation of GeoStore `User` records on first LDAP login
- Synchronization of roles and groups from LDAP authorities to the GeoStore database
- Optional case-insensitive username normalization

The provider takes two constructor arguments:

1. A **`BindAuthenticator`** -- performs LDAP bind authentication using a user search filter
2. A **`GeoStoreLdapAuthoritiesPopulator`** -- resolves the user's groups and roles from LDAP

```xml
<bean id="geostoreLdapProvider"
    class="it.geosolutions.geostore.services.rest.security.UserLdapAuthenticationProvider">
    <constructor-arg>
        <bean class="org.springframework.security.ldap.authentication.BindAuthenticator">
            <constructor-arg ref="contextSource" />
            <property name="userSearch">
                <bean id="userSearch"
                    class="org.springframework.security.ldap.search.FilterBasedLdapUserSearch">
                    <constructor-arg index="0" value="ou=people" />
                    <constructor-arg index="1" value="(uid={0})" />
                    <constructor-arg index="2" ref="contextSource" />
                </bean>
            </property>
        </bean>
    </constructor-arg>
    <constructor-arg>
        <bean class="it.geosolutions.geostore.services.rest.security.GeoStoreLdapAuthoritiesPopulator">
            <constructor-arg ref="contextSource" />
            <constructor-arg value="ou=groups" />   <!-- groupSearchBase -->
            <constructor-arg value="ou=roles" />    <!-- roleSearchBase -->
            <property name="groupSearchFilter" value="(lrGroupOccupant={0})" />
            <property name="roleSearchFilter" value="(roleOccupant={0})" />
            <!-- nested groups support -->
            <property name="enableHierarchicalGroups" value="false" />
            <property name="groupInGroupSearchFilter" value="(lrGroupInGroupOccupant={0})" />
            <property name="maxLevelGroupsSearch" value="3" />
            <!-- the GeoStore convention is:
              * Groups starting with 'ROLE_' will be treated as Auth Roles
              * Groups starting withOUT 'ROLE_' will be treated as Groups
             -->
            <property name="rolePrefix" value="ROLE_" />
            <property name="searchSubtree" value="true" />
            <property name="convertToUpperCase" value="true" />
        </bean>
    </constructor-arg>
</bean>
```

The `BindAuthenticator` uses `FilterBasedLdapUserSearch` to locate users:

| Constructor Arg | Value | Description |
|-----------------|-------|-------------|
| `index="0"` | `ou=people` | The search base relative to the root DN |
| `index="1"` | `(uid={0})` | The LDAP filter where `{0}` is replaced by the login username |
| `index="2"` | `contextSource` | Reference to the LDAP context source |

### GeoStoreLdapAuthoritiesPopulator Properties

The `GeoStoreLdapAuthoritiesPopulator` extends Spring Security's `DefaultLdapAuthoritiesPopulator`
and adds support for separate group and role search bases, hierarchical groups, and custom
search filters.

| Property | Default | Description |
|----------|---------|-------------|
| `groupSearchFilter` | `(member={0})` | LDAP filter to find a user's groups. `{0}` is replaced by the user's DN; `{1}` by the username. |
| `roleSearchFilter` | `(member={0})` | LDAP filter to find a user's roles. Same placeholder substitution as above. |
| `enableHierarchicalGroups` | `false` | Enable nested/hierarchical group resolution |
| `groupInGroupSearchFilter` | `(member={0})` | Filter for group-in-group lookup when hierarchical groups are enabled |
| `maxLevelGroupsSearch` | `Integer.MAX_VALUE` | Maximum depth for hierarchical group search |
| `rolePrefix` | `ROLE_` | Prefix prepended to role authority names |
| `searchSubtree` | `false` | When `true`, searches the entire subtree under the search base; when `false`, searches one level only |
| `convertToUpperCase` | `true` | Convert role and group names to uppercase |
| `groupRoleAttribute` | `cn` | The LDAP attribute used as the authority name for groups and roles |

!!! note
    The "Default" column shows the Java field defaults in the class. The XML example above
    overrides several of these (e.g., `groupSearchFilter` is set to `(lrGroupOccupant={0})`
    instead of the class default `(member={0})`). Adjust these filters to match your LDAP schema.

### Roles vs. Groups Convention

GeoStore uses the `rolePrefix` property to distinguish between authentication roles and user
groups:

- Authorities whose name starts with **`ROLE_`** are treated as **authentication roles**
  (e.g., `ROLE_ADMIN`, `ROLE_USER`).
- Authorities whose name does **not** start with `ROLE_` are treated as **user groups** and are
  synchronized into the GeoStore `UserGroup` table.

Within the `UserLdapAuthenticationProvider`, role extraction follows this logic:

- An authority ending in `ADMIN` (case-insensitive) promotes the user to `Role.ADMIN`
- An authority ending in `USER` (case-insensitive) sets the user to `Role.USER` (if currently `GUEST`)
- All non-`ROLE_` authorities are added as GeoStore groups

---

## Enabling LDAP

By default, the LDAP authentication provider is commented out in the authentication manager.
Follow these steps to enable it:

### Step 1: Uncomment the LDAP provider

In `geostore-spring-security.xml`, locate the `authentication-manager` element and uncomment the
LDAP provider reference:

```xml
<security:authentication-manager>
    <security:authentication-provider ref='geoStoreUserServiceAuthenticationProvider' />
    <security:authentication-provider ref='geostoreLdapProvider' />
</security:authentication-manager>
```

!!! warning
    Keep the `geoStoreUserServiceAuthenticationProvider` as the first provider. Spring Security
    tries providers in order -- the built-in GeoStore provider handles local database users,
    while the LDAP provider handles directory-based users. If the first provider throws a
    definitive authentication failure, Spring will still try the next provider in the chain.

### Step 2: Set the LDAP connection properties

In `geostore-ovr.properties`, set the LDAP connection details:

```properties
ldap.host=ldap.example.com
ldap.port=389
ldap.root=dc=example,dc=com
```

### Step 3 (Optional): Enable case-insensitive usernames

If your LDAP directory allows users to log in with varying case (e.g., `JDoe` vs `jdoe`),
enable username normalization to prevent duplicate GeoStore user records:

```properties
geostoreLdapProvider.ignoreUsernameCase=true
```

When enabled, all usernames are normalized to uppercase before lookup and storage. If a
legacy user record exists with a different case, it will be automatically updated to the
normalized form.

### Step 4 (Optional): Enable LDAP group synchronization at startup

To synchronize LDAP groups into GeoStore at application startup, uncomment the `LDAPInit` bean:

```xml
<bean id="ldapInitializer" class="it.geosolutions.geostore.init.LDAPInit" lazy-init="false">
    <constructor-arg ref="geostoreLdapProvider" />
</bean>
```

This calls `synchronizeGroups()` on the LDAP provider during initialization, which fetches all
groups from LDAP and creates corresponding `UserGroup` records in GeoStore if they do not
already exist.

---

## Hierarchical Groups

When `enableHierarchicalGroups` is set to `true`, GeoStore resolves nested group memberships:

1. After finding a user's direct groups via `groupSearchFilter`, GeoStore searches for parent
   groups using `groupInGroupSearchFilter`.
2. The search recurses upward, with each level using the discovered group's DN as the search
   parameter.
3. Recursion stops when no new groups are found or the depth reaches `maxLevelGroupsSearch`.

This is useful for LDAP schemas where groups can be members of other groups (e.g., "Engineering"
is a member of "AllStaff").

```xml
<property name="enableHierarchicalGroups" value="true" />
<property name="groupInGroupSearchFilter" value="(lrGroupInGroupOccupant={0})" />
<property name="maxLevelGroupsSearch" value="3" />
```

!!! tip
    Set `maxLevelGroupsSearch` to a reasonable value (e.g., `3` or `5`) to prevent runaway
    recursion in directories with deep or circular group nesting.

---

## LDAP Read-Only User and Group DAOs

GeoStore can optionally read users and groups directly from LDAP using read-only DAO
implementations. When enabled, LDAP replaces the database as the source of truth for user
and group listings (e.g., in admin interfaces). Authentication still uses the standard
`geostoreLdapProvider`.

!!! warning
    These DAOs are **read-only**. Write operations such as creating or deleting users and groups
    through the GeoStore REST API will throw `UnsupportedOperationException` or silently no-op.
    User management must be performed directly in the LDAP directory.

To enable the LDAP DAOs, uncomment the following section in `geostore-spring-security.xml`:

```xml
<bean id="ldapUserDAO"
      class="it.geosolutions.geostore.core.dao.ldap.impl.UserDAOImpl">
    <constructor-arg ref="contextSource"/>
    <property name="searchBase" value="ou=users"/>
    <property name="memberPattern" value="^uid=([^,]+).*$"/>
    <property name="attributesMapper">
        <map>
            <entry key="mail" value="email"/>
            <entry key="givenName" value="fullname"/>
            <entry key="description" value="description"/>
        </map>
    </property>
</bean>

<bean id="ldapUserGroupDAO"
      class="it.geosolutions.geostore.core.dao.ldap.impl.UserGroupDAOImpl">
    <constructor-arg ref="contextSource"/>
    <property name="searchBase" value="ou=roles"/>
    <property name="addEveryOneGroup" value="true"/>
</bean>

<alias name="ldapUserGroupDAO" alias="userGroupDAO"/>
<alias name="ldapUserDAO" alias="userDAO"/>
```

### UserDAOImpl Properties

| Property | Default | Description |
|----------|---------|-------------|
| `searchBase` | `""` | LDAP subtree to search for users (relative to root DN) |
| `baseFilter` | `cn=*` | Base LDAP filter applied to all user searches |
| `nameAttribute` | `cn` | LDAP attribute mapped to the GeoStore user name |
| `memberPattern` | `^(.*)$` | Regex to extract the username from a group member DN (e.g., `^uid=([^,]+).*$` extracts the `uid` part) |
| `attributesMapper` | `{}` | Map of LDAP attribute names to GeoStore `UserAttribute` names |
| `adminRoleGroup` | `ADMIN` | Name of the LDAP group that grants `ADMIN` role (case-insensitive) |

### UserGroupDAOImpl Properties

| Property | Default | Description |
|----------|---------|-------------|
| `searchBase` | `""` | LDAP subtree to search for groups (relative to root DN) |
| `baseFilter` | `cn=*` | Base LDAP filter applied to all group searches |
| `nameAttribute` | `cn` | LDAP attribute mapped to the GeoStore group name |
| `memberAttribute` | `member` | LDAP attribute containing the list of group members |
| `addEveryOneGroup` | `false` | When `true`, a virtual `everyone` group is appended to search results |

### LdapBaseDAOImpl Properties (inherited by both DAOs)

| Property | Default | Description |
|----------|---------|-------------|
| `searchBase` | `""` | LDAP search base relative to root DN |
| `baseFilter` | `cn=*` | Base filter combined with all searches |
| `nameAttribute` | `cn` | Attribute used for entity name |
| `descriptionAttribute` | `description` | Attribute used for entity description |
| `sortEnabled` | `false` | Enable LDAP-side sorting (requires server support) |

---

## Combined LDAP + OAuth2

GeoStore supports using LDAP and OAuth2/OIDC authentication simultaneously. Each mechanism
handles a different authentication path:

- **LDAP** handles direct username/password authentication (HTTP Basic, form login)
- **OAuth2/OIDC** handles browser-based Single Sign-On and bearer token authentication

Both providers are registered in the Spring Security authentication manager:

```xml
<security:authentication-manager>
    <security:authentication-provider ref='geoStoreUserServiceAuthenticationProvider' />
    <security:authentication-provider ref='geostoreLdapProvider' />
</security:authentication-manager>
```

The OAuth2/OIDC filters are registered separately in the HTTP security filter chain:

```xml
<security:http auto-config="true" create-session="never">
    <security:http-basic entry-point-ref="restAuthenticationEntryPoint"/>
    <security:custom-filter ref="oidcOpenIdFilter" before="OPENID_FILTER"/>
    <!-- ... other filters ... -->
</security:http>
```

!!! tip
    When combining LDAP with OAuth2, ensure the usernames match across both systems, or configure
    the OAuth2 `principalKey` property to resolve to the same username used in LDAP. For example,
    if LDAP uses `uid` and your OIDC provider returns the username in the `preferred_username`
    claim, set `oidcOAuth2Config.principalKey=preferred_username`.

!!! note
    Users authenticated via LDAP are automatically provisioned in GeoStore's database with their
    LDAP-derived roles and groups. Users authenticated via OAuth2/OIDC are provisioned separately
    (when `autoCreateUser=true`). If the same user authenticates through both mechanisms, they
    will share the same GeoStore `User` record as long as the resolved username matches.

---

## Troubleshooting

### Common Issues

**Connection refused or timeout**

: Verify that `ldap.host` and `ldap.port` are correct and that the LDAP server is reachable
  from the GeoStore host. Check firewall rules for port `389` (LDAP) or `636` (LDAPS).

**User not found after successful bind**

: Ensure the `userSearch` base (`ou=people`) and filter (`(uid={0})`) match your directory
  structure. Use an LDAP browser (e.g., Apache Directory Studio) to verify the user entry
  exists under the expected DN.

**No roles or groups assigned**

: Check that `groupSearchBase`, `roleSearchBase`, `groupSearchFilter`, and `roleSearchFilter`
  match your LDAP schema. The `{0}` placeholder is replaced by the user's full DN -- make sure
  the filter attribute contains DNs, not simple usernames.

**Duplicate user records with different case**

: Enable `geostoreLdapProvider.ignoreUsernameCase=true` to normalize all LDAP usernames to
  uppercase. Existing case-variant records will be consolidated on next login.

### Enabling Debug Logging

To see detailed LDAP search and bind operations, set the following log levels in your logging
configuration:

```properties
log4j.logger.org.springframework.security.ldap=DEBUG
log4j.logger.it.geosolutions.geostore.services.rest.security.UserLdapAuthenticationProvider=DEBUG
log4j.logger.it.geosolutions.geostore.services.rest.security.GeoStoreLdapAuthoritiesPopulator=DEBUG
```
