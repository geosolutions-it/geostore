/* ====================================================================
 *
 * Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTUserService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.RESTUser;
import it.geosolutions.geostore.services.rest.model.UserList;
import it.geosolutions.geostore.services.rest.utils.GeoStorePrincipal;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Class RESTUserServiceImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 * 
 */
public class RESTUserServiceImpl implements RESTUserService {

    private final static Logger LOGGER = Logger.getLogger(RESTUserServiceImpl.class);

    private UserService userService;

    /**
     * @param userService the userService to set
     */
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#insert(it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long insert(SecurityContext sc, User user) {
        if (user == null) {
            throw new BadRequestWebEx("User is null");
        }
        if (user.getId() != null) {
            throw new BadRequestWebEx("Id should be null");
        }

        long id = -1;
        try {
            //
            // Parsing UserAttributes list
            //
            List<UserAttribute> usAttribute = user.getAttribute();

            if (usAttribute != null) {
                if (usAttribute.size() > 0) {
                    user.setAttribute(usAttribute);
                }
            }

            id = userService.insert(user);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx(e.getMessage());
        }

        return id;
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#update(long, it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long update(SecurityContext sc, long id, User user) {
        try {
            User authUser = extractAuthUser(sc);

            User old = userService.get(id);
            if (old == null) {
                throw new NotFoundWebEx("User not found");
            }

            boolean userUpdated = false;
            if (authUser.getRole().equals(Role.ADMIN)) {
                String npw = user.getNewPassword();
                if (npw != null) {
                    old.setNewPassword(user.getNewPassword());
                    userUpdated = true;
                }

                Role nr = user.getRole();
                if (nr != null) {
                    old.setRole(nr);
                    userUpdated = true;
                }

                UserGroup group = user.getGroup();
                if (group != null) {
                    old.setGroup(group);
                    userUpdated = true;
                }
            } else if (old.getName().equals(authUser.getName())) { // Check if the User is the same
                String npw = user.getNewPassword();
                if (npw != null) {
                    old.setNewPassword(user.getNewPassword());
                    userUpdated = true;
                }
            }

            if (userUpdated) {
                id = userService.update(old);

                //
                // Creating a new User Attribute list (updated).
                //
                List<UserAttribute> attributeDto = user.getAttribute();

                if (attributeDto != null) {
                    Iterator<UserAttribute> iteratorDto = attributeDto.iterator();

                    List<UserAttribute> attributes = new ArrayList<UserAttribute>();
                    while (iteratorDto.hasNext()) {
                        UserAttribute aDto = iteratorDto.next();

                        UserAttribute a = new UserAttribute();
                        a.setValue(aDto.getValue());
                        a.setName(aDto.getName());
                        attributes.add(a);
                    }

                    if (attributes.size() > 0) {
                        userService.updateAttributes(id, attributes);
                    }
                }

                return id;
            } else
                return -1;

        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx(e.getMessage());
        }
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#delete(long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        boolean ret = userService.delete(id);
        if (!ret) {
            throw new NotFoundWebEx("User not found");
        }
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#get(long)
     */
    @Override
    public User get(SecurityContext sc, long id, boolean includeAttributes) throws NotFoundWebEx {
        if (id == -1) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Retriving dummy data !");
            }

            //
            // return test instance
            //
            User user = new User();
            user.setName("dummy name");
            return user;
        }

        User authUser = userService.get(id);
        if (authUser == null) {
            throw new NotFoundWebEx("User not found");
        }

        User ret = new User();
        ret.setId(authUser.getId());
        ret.setName(authUser.getName());
        // ret.setPassword(authUser.getPassword()); // NO! password should not be sent out of the server!
        ret.setRole(authUser.getRole());
        ret.setGroup(authUser.getGroup());
        if (includeAttributes) {
            ret.setAttribute(authUser.getAttribute());
        }
        return ret;
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserService#get(java.lang.String)
     */
    @Override
    public User get(SecurityContext sc, String name, boolean includeAttributes)
            throws NotFoundWebEx {
        if (name == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User Name is null !");
            }
            throw new BadRequestWebEx("User name is null");
        }

        User ret;
        try {
            ret = userService.get(name);
            if (includeAttributes) {
                ret.setAttribute(ret.getAttribute());
            } else {
                ret.setAttribute(null);
            }
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx("User not found");
        }

        return ret;
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public UserList getAll(SecurityContext sc, Integer page, Integer entries)
            throws BadRequestWebEx {
        try {
            List<User> userList = userService.getAll(page, entries);
            Iterator<User> iterator = userList.iterator();

            List<RESTUser> restUSERList = new ArrayList<RESTUser>();
            while (iterator.hasNext()) {
                User user = iterator.next();

                RESTUser restUser = new RESTUser(user.getId(), user.getName(), user.getRole());
                restUSERList.add(restUser);
            }

            return new UserList(restUSERList);
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    /*
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserInterface#getCount(java.lang.String)
     */
    @Override
    public long getCount(SecurityContext sc, String nameLike) {
        nameLike = nameLike.replaceAll("[*]", "%");
        return userService.getCount(nameLike);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTUserService#getAuthUserDetails (javax.ws.rs.core.SecurityContext)
     */
    @Override
    public User getAuthUserDetails(SecurityContext sc, boolean includeAttributes) {
        User authUser = extractAuthUser(sc);

        User ret = null;
        try {
            authUser = userService.get(authUser.getName());

            if (authUser != null) {
                ret = new User();
                ret.setId(authUser.getId());
                ret.setName(authUser.getName());
                // ret.setPassword(authUser.getPassword()); // NO! password should not be sent out of the server!
                ret.setRole(authUser.getRole());
                ret.setGroup(authUser.getGroup());
                if (includeAttributes) {
                    ret.setAttribute(authUser.getAttribute());
                }
            }

        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx("User not found");
        }

        return ret;
    }

    /**
     * @return User - The authenticated user that is accessing this service, or null if guest access.
     */
    private User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if (sc == null) {
            throw new InternalErrorWebEx("Missing auth info");
        } else {
            Principal principal = sc.getUserPrincipal();
            if (principal == null) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Missing auth principal");
                }
                throw new InternalErrorWebEx("Missing auth principal");
            }

            /**
             * OLD STUFF
             * 
             * if (!(principal instanceof GeoStorePrincipal)) { if (LOGGER.isInfoEnabled()) { LOGGER.info("Mismatching auth principal"); } throw new
             * InternalErrorWebEx("Mismatching auth principal (" + principal.getClass() + ")"); }
             * 
             * GeoStorePrincipal gsp = (GeoStorePrincipal) principal;
             * 
             * // // may be null if guest // User user = gsp.getUser();
             * 
             * LOGGER.info("Accessing service with user " + (user == null ? "GUEST" : user.getName()));
             **/

            // Allow old stuff while complete integration of the functionalities
            User user = null;
            if (principal instanceof GeoStorePrincipal){
                // Old way (with GeoStoreAuthInterceptor)
                GeoStorePrincipal gsp = (GeoStorePrincipal) principal;
                user = gsp.getUser();
                if (LOGGER.isInfoEnabled()) {
                    // may be null if guest 
                    LOGGER.info("Accessing service with user " + (user == null ? "GUEST" : user.getName()));
                }
            }else if (!(principal instanceof UsernamePasswordAuthenticationToken)) {
                // Unknown principal
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Mismatching auth principal");
                }
                throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass()
                        + ")");
            }else{
                // Spring security way
                UsernamePasswordAuthenticationToken usrToken = (UsernamePasswordAuthenticationToken) principal;
                user = new User();
                user.setName(usrToken == null ? "GUEST" : usrToken.getName());

                for (GrantedAuthority authority : usrToken.getAuthorities()) {
                    if (authority != null) {
                        if (authority.getAuthority() != null
                                && authority.getAuthority().contains("ADMIN"))
                            user.setRole(Role.ADMIN);

                        if (authority.getAuthority() != null
                                && authority.getAuthority().contains("USER") && user.getRole() == null)
                            user.setRole(Role.USER);

                        if (user.getRole() == null)
                            user.setRole(Role.GUEST);
                    }
                }
            }

            LOGGER.info("Accessing service with user " + user.getName() + " and role "
                    + user.getRole());

            return user;
        }
    }

    @Override
    public UserList getUserList(SecurityContext sc, String nameLike, Integer page, Integer entries,
            boolean includeAttributes) throws BadRequestWebEx {

        nameLike = nameLike.replaceAll("[*]", "%");

        try {
            List<User> userList = userService.getAll(page, entries, nameLike, includeAttributes);
            Iterator<User> iterator = userList.iterator();

            List<RESTUser> restUSERList = new ArrayList<RESTUser>();
            while (iterator.hasNext()) {
                User user = iterator.next();

                RESTUser restUser = new RESTUser(user.getId(), user.getName(), user.getRole());
                restUSERList.add(restUser);
            }

            return new UserList(restUSERList);
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }
}
