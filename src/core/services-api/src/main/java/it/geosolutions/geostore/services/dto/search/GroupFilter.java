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
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;

/** Filter by group name */
@XmlRootElement(name = "Group")
public class GroupFilter extends SearchFilter implements Serializable {

    private List<String> names = new ArrayList<>();

    private SearchOperator operator;

    /** */
    public GroupFilter() {}

    /**
     * @param names
     * @param operator
     */
    public GroupFilter(List<String> names, SearchOperator operator) {
        this.names = names;
        setOperator(operator);
    }

    /** @return the names */
    public List<String> getNames() {
        return names;
    }

    /** @param names the names to set */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /** @return the operator */
    public SearchOperator getOperator() {
        return operator;
    }

    /** @param operator the operator to set */
    public final void setOperator(SearchOperator operator) {
        checkOperator(operator);
        this.operator = operator;
    }

    public static void checkOperator(SearchOperator operator) {
        if (operator != SearchOperator.EQUAL_TO
                && operator != SearchOperator.LIKE
                && operator != SearchOperator.ILIKE
                && operator != SearchOperator.IN)
            throw new IllegalArgumentException(
                    "Only EQUAL_TO, LIKE, ILIKE, or IN operators are acceptable");
    }

    @Override
    public void accept(FilterVisitor visitor) throws BadRequestServiceEx, InternalErrorServiceEx {
        visitor.visit(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName()
                + '['
                + (operator != null ? operator : "!op!")
                + ' '
                + (names != null ? names : "[!names!]")
                + ']';
    }
}
