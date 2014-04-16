/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 * 
 *  GPLv3 + Classpath exception
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.init;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.security.password.GeoStoreAESEncoder;
import it.geosolutions.geostore.core.security.password.GeoStorePasswordEncoder;
import it.geosolutions.geostore.core.security.password.PwEncoder;
import it.geosolutions.geostore.init.model.InitUserList;
import it.geosolutions.geostore.services.CategoryService;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.exception.ReservedUserGroupNameEx;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.utils.GeoStoreJAXBContext;

import java.io.File;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;

/**
 * 
 * @author ETj (etj at geo-solutions.it)
 */
public class GeoStoreInit implements InitializingBean {

    private final static Logger LOGGER = Logger.getLogger(GeoStoreInit.class);

    protected UserService userService;

    protected CategoryService categoryService;

    protected UserGroupService userGroupService;
    
    protected File userListInitFile = null;

    protected File categoryListInitFile = null;
    
    protected File userGroupListInitFile = null;

    /**
     * The password encoder to be set as default
     */
    protected GeoStorePasswordEncoder passwordEncoder = null;
    /**
     * If set to true, the recoding of the password is automatic
     */
    protected boolean allowPasswordRecoding = false;

	@Override
    public void afterPropertiesSet() throws Exception {

        LOGGER.info("===== Starting GeoStore services =====");
        //initialize password encoding
        initPasswordEncoding();
        long catCnt = categoryService.getCount(null);
        if (catCnt == 0) {
            LOGGER.warn("No category found.");
            if (categoryListInitFile != null) {
                LOGGER.warn("Initializing categories from file " + categoryListInitFile);
                initCategories(categoryListInitFile);	
            } else {
                LOGGER.info("No category initializer defined.");
            }
        } else {
            LOGGER.info("Categories already in db: " + catCnt);
        }

        long userGroupCnt = userGroupService.getAll(null, null).size();
        if (userGroupCnt == 0) {
            LOGGER.warn("No usersgroup found.");
            if (userGroupListInitFile != null) {
                LOGGER.warn("Initializing users from file " + userGroupListInitFile);
                initUsersGroup(userGroupListInitFile);
            } else {
                LOGGER.info("No usersgroup initializer defined.");
            }
        } else {
            LOGGER.info("UsersGroup already in db: " + userGroupCnt);
            
        }
        
        long userCnt = userService.getCount(null);
        if (userCnt == 0) {
            LOGGER.warn("No user found.");
            if (userListInitFile != null) {
                LOGGER.warn("Initializing users from file " + userListInitFile);
                initUsers(userListInitFile);
            } else {
                LOGGER.info("No user initializer defined.");
            }
        } else {
            LOGGER.info("Users already in db: " + userCnt);
        }
    }
    
    private void initPasswordEncoding(){
    	LOGGER.info("=== Set up the security system   ====");
    	LOGGER.info("Encoding Type:" + passwordEncoder.getEncodingType());
        
    	PwEncoder.setEncoder(this.passwordEncoder);
    	//check and convert passwords
    	try {
			List<User> users = userService.getAll(0, 1);
			if(users != null && users.size()>0){
				//check password encription of the first user availabe
				boolean responsible = this.passwordEncoder.isResponsibleForEncoding(users.get(0).getPassword());
				
				//if the current password encoder is the responsible for the encoding of the password 
				//we suppose the conversion is already happended
				if(responsible) return;
				LOGGER.warn("=======================================================================================");
				LOGGER.warn("   WARNING: USERS PASSWORDS ARE NOT SYNCRONIZED WITH THE CONFIGURED PASSWORD ENCODER   ");
				LOGGER.warn("=======================================================================================");
				//check if the password is old legacy, so GeoStoreAESEncoder is the responsible for the encoding
				GeoStoreAESEncoder e = new GeoStoreAESEncoder();
				boolean isLegacy = e.isResponsibleForEncoding(users.get(0).getPassword());
				if(isLegacy){
					if (!allowPasswordRecoding){
						LOGGER.warn("To convert old passwords to new ones use geostoreInitializer.allowPasswordRecoding=true");
						return;
					}
					LOGGER.info("Starting password conversion...");
					for(User u : userService.getAll(null, null)){
						String p = u.getPassword();
						if (e.isResponsibleForEncoding(p)){
							String dec = e.decode(p);
							String enc = this.passwordEncoder.encodePassword(dec.toCharArray(), null);
							u.setPassword(enc);
							try {
								userService.update(u);
								LOGGER.info("UPDATED USER PASSWORD for the user:"+u.getName());
							} catch (NotFoundServiceEx e1) {
								LOGGER.error("===> ERROR updating user password for user" + u.getName() );
								
							}
						}
					}
					LOGGER.info("Password conversion finished!");
				}
				
			}
		} catch (BadRequestServiceEx e) {
			//error getting users is not a problem at this stage.
			//e.printStackTrace();
		}
    }

    private void initCategories(File file) {
        try {
            JAXBContext context = GeoStoreJAXBContext.getContext();
            CategoryList list = (CategoryList) context.createUnmarshaller().unmarshal(file);
            for (Category item : list.getList()) {
                LOGGER.info("Adding category " + item);
                categoryService.insert(item);
            }
        } catch (JAXBException ex) {
            throw new RuntimeException("Error reading categories init file " + file, ex);
        } catch (Exception e) {
            LOGGER.error("Error while initting categories. Rolling back.", e);
            List<Category> removeList;
            try {
                removeList = categoryService.getAll(null, null);
            } catch (BadRequestServiceEx ex) {
                throw new RuntimeException(
                        "Error while rolling back categories initialization. Your DB may now contain an incomplete category list. Please check manually.",
                        e);
            }

            for (Category cat : removeList) {
                categoryService.delete(cat.getId());
            }

            throw new RuntimeException("Error while initting categories.");
        }

    }

    private void initUsers(File file) {
        try {
            userService.insertSpecialUsers();
            JAXBContext context = getUserContext();

            InitUserList list = (InitUserList) context.createUnmarshaller().unmarshal(file);
            for (User user : list.getList()) {
                LOGGER.info("Adding user " + user);
                userService.insert(user);
            }
        } catch (JAXBException ex) {
            throw new RuntimeException("Error reading users init file " + file, ex);
        } catch (Exception e) {
            LOGGER.error("Error while initting users. Rolling back.", e);
            List<User> removeList;
            try {
                removeList = userService.getAll(null, null);
            } catch (BadRequestServiceEx ex) {
                throw new RuntimeException(
                        "Error while rolling back user initialization. Your DB may now contain an incomplete user list. Please check manually.",
                        e);
            }

            for (User user : removeList) {
                userService.delete(user.getId());
            }

            throw new RuntimeException("Error while initting users.");
        }
    }
    
    private void initUsersGroup(File file) throws NotFoundServiceEx, BadRequestServiceEx {
        try {
            userGroupService.insertSpecialUsersGroups();
            JAXBContext context = GeoStoreJAXBContext.getContext();
            UserGroupList list = (UserGroupList) context.createUnmarshaller().unmarshal(file);
            for (RESTUserGroup userGroup : list.getUserGroupList()) {
                LOGGER.info("Adding user group " + userGroup);
                UserGroup ug = new UserGroup();
                ug.setGroupName(userGroup.getGroupName());
                ug.setDescription(userGroup.getDescription());
                try{
                userGroupService.insert(ug);
                }
                catch(ReservedUserGroupNameEx e){
                    // If  a reserved username is in the init usergroup file log the exception and skip this insertion
                    LOGGER.warn(e.getMessage());
                }
            }
        } catch (JAXBException ex) {
            throw new RuntimeException("Error reading usersgroup init file " + file, ex);
        } catch (Exception e) {
            LOGGER.error("Error while initting usersgroups. Rolling back.", e);
            List<UserGroup> removeList;
            try {
                removeList = userGroupService.getAll(null, null);
            } catch (BadRequestServiceEx ex) {
                throw new RuntimeException(
                        "Error while rolling back usergroup initialization. Your DB may now contain an incomplete usergroup list. Please check manually.",
                        e);
            }
            for (UserGroup ug : removeList) {
                userGroupService.delete(ug.getId());
            }
            throw new RuntimeException("Error while initting usersgroup.");
        }
    }

    private static JAXBContext getUserContext() {

        List<Class> allClasses = GeoStoreJAXBContext.getGeoStoreClasses();
        allClasses.add(InitUserList.class);

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Initializing JAXBContext with " + allClasses.size() + " classes "
                    + allClasses);

        try {
            return JAXBContext.newInstance(allClasses.toArray(new Class[allClasses.size()]));
        } catch (JAXBException ex) {
            LOGGER.error("Can't create GeoStore context: " + ex.getMessage(), ex);
            return null;
        }
    }

    // ==========================================================================

    public void setUserListInitFile(File userListInitFile) {
        this.userListInitFile = userListInitFile;
    }

    public void setCategoryListInitFile(File categoryListInitFile) {
        this.categoryListInitFile = categoryListInitFile;
    }
    
    public void setUserGroupListInitFile(File userGroupListInitFile) {
        this.userGroupListInitFile = userGroupListInitFile;
    }

    // ==========================================================================

    public void setCategoryService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }
    

    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }
    
    // ==========================================================================
    
    public GeoStorePasswordEncoder getPasswordEncoder() {
		return passwordEncoder;
	}

	public void setPasswordEncoder(GeoStorePasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	// ===========================================================================
	public boolean isAllowPasswordRecoding() {
		return allowPasswordRecoding;
	}

	public void setAllowPasswordRecoding(boolean allowPasswordRecoding) {
		this.allowPasswordRecoding = allowPasswordRecoding;
	}

}
