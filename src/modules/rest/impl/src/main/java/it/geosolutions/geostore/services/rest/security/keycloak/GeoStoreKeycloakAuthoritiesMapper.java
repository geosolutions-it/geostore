package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;
import org.apache.log4j.Logger;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A granted authorities mapper aware of the difference that there is in GeoStore between Role and Groups.
 */
public class GeoStoreKeycloakAuthoritiesMapper implements GrantedAuthoritiesMapper{

    private Set<UserGroup> groups;

    private Role role;

    private static final String ROLE_PREFIX="ROLE_";

    private Map<String,String> roleMappings;

    private final static Logger LOGGER = Logger.getLogger(GeoStoreKeycloakAuthoritiesMapper.class);


    public GeoStoreKeycloakAuthoritiesMapper(Map<String,String> roleMappings){
        this.roleMappings=roleMappings;
        if (LOGGER.isDebugEnabled() && roleMappings!=null)
            LOGGER.debug("Role mappings have been configured....");
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        HashSet<GrantedAuthority> mapped = new HashSet<>(
                authorities.size());
        for (GrantedAuthority authority : authorities) {
            mapped.add(mapAuthority(authority.getAuthority()));
        }
        return mapped;
    }

    void mapAuthorities(List<String> roles){
        if (roles !=null) roles.forEach(r->mapStringAuthority(r));
    }

    private GrantedAuthority mapAuthority(String name) {
        String authName=mapStringAuthority(name);
        return new SimpleGrantedAuthority(authName);
    }

    private String mapStringAuthority(String name) {
        name=name.toUpperCase();
        String authName;
        if (roleMappings==null) authName=defaultRoleMatch(name);
        else authName=userMappingsMatch(name);
        if (authName==null){
            // if not a role then is a group.
            authName=name;
            addGroup(authName);
        }
        return authName;
    }

    private String userMappingsMatch(String name){
        if (LOGGER.isDebugEnabled()){
            LOGGER.info("Using the configured role mappings..");
        }
        String result= roleMappings.get(name);
        if (result!=null) {
            try {
                Role role=Role.valueOf(result);
                if (getRole()==null || role.ordinal()<getRole().ordinal()) setRole(role);
                result=ROLE_PREFIX+result;
            } catch (Exception e){
                String message="The value " + result +" set to match role "+name+ " is not a valid Role. You should use one of ADMIN,USER,GUEST";
                LOGGER.error(message,e);
                throw new RuntimeException(message);
            }
        }
        return result;
    }

    private String defaultRoleMatch(String name){
        if (LOGGER.isDebugEnabled()){
            LOGGER.info("Matching the keycloak role to geostore roles based on name equality...");
        }
        String result=null;
        if (name.equalsIgnoreCase(Role.ADMIN.name())){
            result=ROLE_PREFIX+Role.ADMIN.name();
            setRole(Role.ADMIN);
        } else if (name.equals(Role.USER.name())){
            result=ROLE_PREFIX+Role.USER.name();
            setRole(Role.ADMIN);
        } else if (name.equals(Role.GUEST.name())){
            result=ROLE_PREFIX+Role.GUEST.name();
            setRole(Role.ADMIN);
        }
        return result;
    }

    /**
     * @return the found UserGroups.
     */
    public Set<UserGroup> getGroups() {
        if (groups==null) return Collections.emptySet();
        return groups;
    }

    /**
     * @return the found role.
     */
    public Role getRole() {
        if (role==null) return Role.USER;
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
