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
package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.CategoryService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.UserList;
import it.geosolutions.geostore.services.rest.utils.GeoStoreJAXBContext;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXB;
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

    protected File userListInitFile = null;
    protected File categoryListInitFile = null;

    @Override
    public void afterPropertiesSet() throws Exception {

        LOGGER.info("===== Starting GeoStore services =====");

        long catCnt = categoryService.getCount(null);
        if (catCnt == 0) {
            LOGGER.warn("No category found.");
            if(categoryListInitFile != null) {
                LOGGER.warn("Initializing categories from file " + categoryListInitFile);
                initCategories(categoryListInitFile);
            } else {
                LOGGER.info("No category initializer defined.");
            }
        } else {
            LOGGER.info("Categories already in db: " + catCnt);
        }

        long userCnt = userService.getCount(null);
        if (userCnt == 0) {
            LOGGER.warn("No user found.");
            if(userListInitFile != null) {
                LOGGER.warn("Initializing users from file " + userListInitFile);
                initUsers(userListInitFile);
            } else {
                LOGGER.info("No user initializer defined.");
            }
        } else {
            LOGGER.info("Users already in db: " + userCnt);
        }
    }


    private void initCategories(File file) {
        try {
            JAXBContext context = GeoStoreJAXBContext.getContext();
            CategoryList list = (CategoryList)context.createUnmarshaller().unmarshal(file);
            for (Category item : list.getList()) {
                LOGGER.info("Adding category " + item);
                categoryService.insert(item);
            }
        } catch (JAXBException ex) {
            throw new RuntimeException("Error reading categories init file "+file, ex);
        } catch (Exception e) {
            LOGGER.error("Error while initting categories. Rolling back.", e);
            List<Category> removeList;
            try {
                removeList = categoryService.getAll(null, null);
            } catch (BadRequestServiceEx ex) {
                throw new RuntimeException("Error while rolling back categories initialization. Your DB may now contain an incomplete category list. Please check manually.", e);
            }

            for (Category cat : removeList) {
                categoryService.delete(cat.getId());
            }

            throw new RuntimeException("Error while initting categories.");
        }
        
    }

    private void initUsers(File file) {
        try {
            JAXBContext context = GeoStoreJAXBContext.getContext();
            UserList list = (UserList)context.createUnmarshaller().unmarshal(file);
            for (User user : list.getList()) {
                LOGGER.info("Adding user " + user);
                userService.insert(user);
            }
        } catch (JAXBException ex) {
            throw new RuntimeException("Error reading users init file "+file, ex);
        } catch (Exception e) {
            LOGGER.error("Error while initting users. Rolling back.", e);
            List<User> removeList;
            try {
                removeList = userService.getAll(null, null);
            } catch (BadRequestServiceEx ex) {
                throw new RuntimeException("Error while rolling back user initialization. Your DB may now contain an incomplete user list. Please check manually.", e);
            }

            for (User user : removeList) {
                userService.delete(user.getId());
            }

            throw new RuntimeException("Error while initting users.");
        }
    }

    //==========================================================================

    public void setUserListInitFile(File userListInitFile) {
        this.userListInitFile = userListInitFile;
    }

    public void setCategoryListInitFile(File categoryListInitFile) {
        this.categoryListInitFile = categoryListInitFile;
    }

    //==========================================================================

    public void setCategoryService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
