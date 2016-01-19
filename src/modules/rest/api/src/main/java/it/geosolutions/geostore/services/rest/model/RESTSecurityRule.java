/*
 * ====================================================================
 *
 * Copyright (C) 2007 - 2014 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.model;

import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class RESTSecurityRule.
 * 
 * @author Mauro Bartolomeoli (mauro.bartolomeoli at geo-solutions.it)
 * 
 */
@XmlRootElement(name = "SecurityRule")
public class RESTSecurityRule {
	
	private RESTUser user = null;
	
	private RESTUserGroup group = null;
	
	private boolean canRead = false;
	
	private boolean canWrite = false;

	public RESTSecurityRule(SecurityRule rule) {
		if(rule.getUser() != null) {
			User ruleUser = rule.getUser();
			user = new RESTUser();
			user.setId(ruleUser.getId());
			user.setName(ruleUser.getName());
		}
		if(rule.getGroup() != null) {
			UserGroup ruleGroup = rule.getGroup();
			group = new RESTUserGroup();
			group.setId(ruleGroup.getId());
			group.setGroupName(ruleGroup.getGroupName());
		}
		canRead = rule.isCanRead();
		canWrite = rule.isCanWrite();
	}
	
	public RESTSecurityRule() {
		
	}
	
	public RESTSecurityRule(RESTUser user, boolean canRead, boolean canWrite) {
		super();
		this.user = user;
		this.canRead = canRead;
		this.canWrite = canWrite;
	}

	public RESTSecurityRule(RESTUserGroup group, boolean canRead, boolean canWrite) {
		super();
		this.group = group;
		this.canRead = canRead;
		this.canWrite = canWrite;
	}
	
	
	public RESTUser getUser() {
		return user;
	}

	public void setUser(RESTUser user) {
		this.user = user;
	}

	public RESTUserGroup getGroup() {
		return group;
	}

	public void setGroup(RESTUserGroup group) {
		this.group = group;
	}

	public boolean isCanRead() {
		return canRead;
	}

	public void setCanRead(boolean canRead) {
		this.canRead = canRead;
	}

	public boolean isCanWrite() {
		return canWrite;
	}

	public void setCanWrite(boolean canWrite) {
		this.canWrite = canWrite;
	}

	
	
}
