/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.keycloak;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static it.geosolutions.geostore.core.model.enums.GroupReservedNames.EVERYONE;

/**
 * Class responsible to map a Search to a KeycloakQuery.
 */
class KeycloakSearchMapper {

    private static final List<Integer> SUPPORTED_OPERATORS= Arrays.asList(Filter.OP_AND,Filter.OP_EQUAL,Filter.OP_ILIKE,Filter.OP_NOT_EQUAL);



    enum FilterType {
        NAME,
        GROUPNAME,
        ENABLED,
        NOT_FOUND
    }


    /**
     * Convert the ISearch to a keycloak query.
     * @param search the search instance.
     * @return the keycloak query.
     */
    KeycloakQuery keycloackQuery(ISearch search){
        KeycloakQuery query=new KeycloakQuery();
        search=getNestedSearch(search);
        List<Filter> filters=search.getFilters();
        boolean allSupported=filters.stream().allMatch(f->SUPPORTED_OPERATORS.contains(f.getOperator()));
        if(!allSupported){
            throw new UnsupportedOperationException("Keycloak DAO cannot filter for more then one value");
        }
        if (!filters.isEmpty()) {
            for (Filter filter:filters)
               processFilter(query,filter);
        }
        Integer page=search.getPage();
        Integer maxRes=search.getMaxResults();
        if (page>-1) {
            int startIndex=maxRes!=null? page*maxRes:page;
            if (startIndex>0) startIndex ++;
            if (maxRes!=null) query.setStartIndex(startIndex);
        }
        if (maxRes>-1)
            query.setMaxResults(maxRes);
        return query;
    }


    private void processFilter(KeycloakQuery query,Filter filter){
        if (filter.getOperator()==Filter.OP_AND) return;
        else if (filter.getOperator()==Filter.OP_EQUAL)
            query.setExact(true);
        else if (filter.getOperator()!=Filter.OP_ILIKE && filter.getOperator() != Filter.OP_NOT_EQUAL)
            throw new UnsupportedOperationException("Keycloak DAO supports only EQUAL and LIKE operators");

        if (filter.getOperator()==Filter.OP_NOT_EQUAL && filter.getValue().toString().equalsIgnoreCase(EVERYONE.groupName()))
            query.setSkipEveryBodyGroup(true);
        else {
            String property = filter.getProperty().toUpperCase();
            FilterType type;
            try {
                type = FilterType.valueOf(property);
            } catch (UnsupportedOperationException e) {
                type = FilterType.NOT_FOUND;
            }
            switch (type) {
                case NAME:
                    query.setUserName(filter.getValue().toString());
                    break;
                case GROUPNAME:
                    query.setGroupName(filter.getValue().toString());
                    break;
                case ENABLED:
                    query.setEnabled(Boolean.valueOf(filter.getValue().toString()));
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * If the given search has filters working on nested objects,
     * replaces them with the nested filter.
     * @param search
     * @return
     */
    protected ISearch getNestedSearch(ISearch search) {
        List<Filter> newFilters = new ArrayList<>();
        for (Filter filter : search.getFilters()) {
            Filter nestedFilter = getNestedFilter(filter);
            if (nestedFilter != null) {
                newFilters.add(nestedFilter);
            } else {
                newFilters.add(filter);
            }
        }
        search.getFilters().clear();
        search.getFilters().addAll(newFilters);
        return search;
    }

    /**
     * Returns the internal filter of a nested filter, null otherwise.
     *
     * @param filter the filter.
     * @return the nested filter if any null otherwise.
     */
    protected Filter getNestedFilter(Filter filter) {
        if (filter.getOperator() == Filter.OP_SOME || filter.getOperator() == Filter.OP_ALL) {
            return (Filter)filter.getValue();
        }
        return null;
    }



}
