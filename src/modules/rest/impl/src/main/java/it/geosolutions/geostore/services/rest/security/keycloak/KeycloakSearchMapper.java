package it.geosolutions.geostore.services.rest.security.keycloak;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import org.keycloak.admin.client.resource.RealmResource;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class responsible to map a Search to a KeycloakQuery.
 */
class KeycloakSearchMapper {

    private static final List<Integer> SUPPORTED_OPERATORS= Arrays.asList(Filter.OP_AND,Filter.OP_EQUAL,Filter.OP_ILIKE);



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
        else if (filter.getOperator()!=Filter.OP_ILIKE)
            throw new UnsupportedOperationException("Keycloak DAO supports only EQUAL and LIKE operators");

        String property=filter.getProperty().toUpperCase();
        FilterType type;
        try {
            type=FilterType.valueOf(property);
        } catch (UnsupportedOperationException e){
            type=FilterType.NOT_FOUND;
        }
        switch (type){
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
