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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class AndFilter.
 *
 * @author ETj (etj at geo-solutions.it)
 */
@XmlRootElement(name = "AND")
public class AndFilter extends SearchFilter {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -4055325472578322312L;

    private List<SearchFilter> filters = new ArrayList<SearchFilter>();

    public AndFilter() {}

    public AndFilter(SearchFilter f1, SearchFilter f2, SearchFilter... other) {
        filters.add(f1);
        filters.add(f2);
        filters.addAll(Arrays.asList(other));
    }

    // molto molto brutto, da cambiare se possibile
    @XmlElements({
        @XmlElement(name = "ATTRIBUTE", type = AttributeFilter.class),
        @XmlElement(name = "OR", type = OrFilter.class),
        @XmlElement(name = "AND", type = AndFilter.class),
        @XmlElement(name = "FIELD", type = FieldFilter.class),
        @XmlElement(name = "CATEGORY", type = CategoryFilter.class)
    })
    public List<SearchFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<SearchFilter> filters) {
        this.filters = filters;
    }

    public void add(SearchFilter filter) {
        filters.add(filter);
    }

    @Override
    public void accept(FilterVisitor visitor) throws BadRequestServiceEx, InternalErrorServiceEx {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + filters + '}';
    }
}
