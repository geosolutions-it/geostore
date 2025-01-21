package it.geosolutions.geostore.services.rest.model;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

public class Sort {

    @QueryParam("sortBy")
    private String sortBy;

    @QueryParam("sortOrder")
    @DefaultValue("desc")
    private String sortOrder;

    public Sort() {}

    public Sort(String sortBy, String sortOrder) {
        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }
}
