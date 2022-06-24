package it.geosolutions.geostore.services.rest.security.keycloak;

/**
 * A convenience class to be used to represent a query to the keycloak REST api.
 */
public class KeycloakQuery {

    private Boolean exact;

    private Integer startIndex;

    private Integer maxResults;

    private String groupName;

    private String userName;

    private Boolean enabled;

    /**
     *
     * @return the group name if any, null otherwise.
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     *
     * @param groupName the group name value to filter by.
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     *
     * @return the user name if any, null otherwise.
     */
    public String getUserName() {
        return userName;
    }

    /**
     *
     * @param userName the user name value to filter by.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     *
     * @return the enabled flag of a user if any, null otherwise.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     *
     * @param enabled the enabled value to filter by.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     *
     * @return true if the query is an = query false or null if it is a LIKE one.
     */
    public Boolean getExact() {
        return exact;
    }

    /**
     *
     * @param exact the exact flag.
     */
    public void setExact(Boolean exact) {
        this.exact = exact;
    }

    /**
     *
     * @return the start index if any, null otherwise.
     */
    public Integer getStartIndex() {
        return startIndex;
    }

    /**
     *
     * @param startIndex the start index of a page.
     */
    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    /**
     *
     * @return the max result per page value if any, null otherwise.
     */
    public Integer getMaxResults() {
        return maxResults;
    }

    /**
     *
     * @param maxResults the max result number per page.
     */
    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }


    /**
     * @return true if the query is a = query, false if it is a LIKE one.
     */
    public boolean isExact(){
        return exact!=null && exact.booleanValue();
    }

    @Override
    public String toString() {
        return "KeycloakQuery{" +
                "exact=" + exact +
                ", startIndex=" + startIndex +
                ", maxResults=" + maxResults +
                ", groupName='" + groupName + '\'' +
                ", userName='" + userName + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
