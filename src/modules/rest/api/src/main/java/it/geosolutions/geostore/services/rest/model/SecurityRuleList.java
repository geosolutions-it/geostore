/* ====================================================================
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class SecurityRuleList.
 *
 * @author Mauro Bartolomeoli (mauro.bartolomeoli at geo-solutions.it)
 */
@XmlRootElement(name = "SecurityRuleList")
public class SecurityRuleList implements Iterable<RESTSecurityRule> {

    private List<RESTSecurityRule> list;

    public SecurityRuleList() {}

    /** @param list */
    public SecurityRuleList(List<SecurityRule> list) {
        this.list = new ArrayList<RESTSecurityRule>();
        for (SecurityRule rule : list) {
            this.list.add(new RESTSecurityRule(rule));
        }
    }

    /** @return List<Category> */
    @XmlElement(name = "SecurityRule")
    public List<RESTSecurityRule> getList() {
        return list;
    }

    /** @param list */
    public void setList(List<RESTSecurityRule> list) {
        this.list = list;
    }

    @Override
    public Iterator<RESTSecurityRule> iterator() {
        return list == null ? Collections.EMPTY_LIST.iterator() : list.iterator();
    }
}
