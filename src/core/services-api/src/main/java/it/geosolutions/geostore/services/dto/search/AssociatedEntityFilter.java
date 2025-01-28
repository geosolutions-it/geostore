/*
 *  Copyright (C) 2025 GeoSolutions S.A.S.
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
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;

/**
 * {@link SearchFilter} implementation that represent a filter by name on a {@link
 * it.geosolutions.geostore.core.model.Resource}-associated entity.
 */
public abstract class AssociatedEntityFilter extends SearchFilter implements Serializable {

    private static final List<SearchOperator> VALID_OPERATORS =
            List.of(
                    SearchOperator.EQUAL_TO,
                    SearchOperator.LIKE,
                    SearchOperator.ILIKE,
                    SearchOperator.IN);

    private SearchOperator operator;
    private List<String> values;

    public AssociatedEntityFilter() {}

    public AssociatedEntityFilter(String names, SearchOperator operator) {
        setNames(names);
        setOperator(operator);
    }

    /**
     * Setter to map the deserialized filter names.
     *
     * <p>The names (comma-separated) are then split in individual strings.
     *
     * @param names a comma-separated list of names to filter the resource with
     */
    @XmlElement
    public void setNames(String names) {

        if (names == null) {
            throw new IllegalArgumentException("filter names should not be null");
        }

        this.values = new ArrayList<>(Arrays.asList(names.split(",")));
    }

    public SearchOperator getOperator() {
        return operator;
    }

    public void setOperator(SearchOperator operator) {
        this.operator = operator;
        checkOperator();
    }

    public List<String> values() {
        return values;
    }

    public abstract String property();

    @Override
    public void accept(FilterVisitor visitor) throws BadRequestServiceEx, InternalErrorServiceEx {
        checkOperator();
        visitor.visit(this);
    }

    private void checkOperator() {
        if (!VALID_OPERATORS.contains(operator))
            throw new IllegalArgumentException(
                    "Only EQUAL_TO, LIKE, ILIKE, or IN operators are acceptable");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + '['
                + (operator != null ? operator : "!op!")
                + ' '
                + (values != null ? values : "[!names!]")
                + ']';
    }
}
