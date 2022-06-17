package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A granted authorities mapper aware of the difference that there is in GeoStore between Role and Groups.
 */
public class GeoStoreKeycloakAuthoritiesMapper implements GrantedAuthoritiesMapper{

    private Set<UserGroup> groups;

    private Role role;

    private static final String ROLE_PREFIX="ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        HashSet<GrantedAuthority> mapped = new HashSet<>(
                authorities.size());
        for (GrantedAuthority authority : authorities) {
            mapped.add(mapAuthority(authority.getAuthority()));
        }
        return mapped;
    }

    private GrantedAuthority mapAuthority(String name) {
        name=name.toUpperCase();
        String authName=null;
        if (name.equalsIgnoreCase(Role.ADMIN.name())){
            authName=ROLE_PREFIX+Role.ADMIN.name();
            setRole(Role.ADMIN);
        } else if (name.equals(Role.USER.name())){
            authName=ROLE_PREFIX+Role.USER.name();
            setRole(Role.ADMIN);
        } else if (name.equals(Role.GUEST.name())){
            authName=ROLE_PREFIX+Role.GUEST.name();
            setRole(Role.ADMIN);
        } else {
            // if not a role then is a group.
            authName=name;
            addGroup(authName);
        }
        return new SimpleGrantedAuthority(authName);
    }

    /**
     * @return the found UserGroups.
     */
    public Set<UserGroup> getGroups() {
        return groups;
    }

    /**
     * @return the found role.
     */
    public Role getRole() {
        return role;
    }

    private void setRole(Role role){
        if (this.role==null)this.role=role;
        else if (this.role.ordinal()>role.ordinal()) this.role=role;
    }

    private void addGroup(String groupName){
        if (this.groups==null) this.groups=new HashSet<>();
        UserGroup userGroup=new UserGroup();
        userGroup.setGroupName(groupName);
        userGroup.setEnabled(true);
        this.groups.add(userGroup);
    }
}
