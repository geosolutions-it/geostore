/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.dto.search;

import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Abstract Class SearchFilter.
 *
 * @author ETj (etj at geo-solutions.it)
 */
@XmlRootElement
public abstract class SearchFilter implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -3525374410342234805L;

    public abstract void accept(FilterVisitor visitor)
            throws BadRequestServiceEx, InternalErrorServiceEx;
}
