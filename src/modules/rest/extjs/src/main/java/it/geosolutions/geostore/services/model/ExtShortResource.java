package it.geosolutions.geostore.services.model;

import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.ShortAttributeList;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * An extended version of the {@link ShortResource} class that includes {@link ShortAttributeList}
 * and {@link SecurityRuleList} for the resource.
 */
@XmlRootElement(name = "ShortResource")
public class ExtShortResource extends ShortResource {

    @XmlElement private ShortAttributeList attributeList;
    @XmlElement private SecurityRuleList securityRuleList;

    public ExtShortResource() {}

    private ExtShortResource(Builder builder) {
        this.setId(builder.shortResource.getId());
        this.setName(builder.shortResource.getName());
        this.setDescription(builder.shortResource.getDescription());
        this.setCreation(builder.shortResource.getCreation());
        this.setLastUpdate(builder.shortResource.getLastUpdate());
        this.setCanEdit(builder.shortResource.isCanEdit());
        this.setCanDelete(builder.shortResource.isCanDelete());
        this.setCreator(builder.shortResource.getCreator());
        this.setEditor(builder.shortResource.getEditor());
        this.setAdvertised(builder.shortResource.isAdvertised());

        this.attributeList = builder.attributeList;
        this.securityRuleList = builder.securityRuleList;
    }

    public ShortAttributeList getAttributeList() {
        return attributeList;
    }

    public SecurityRuleList getSecurityRuleList() {
        return securityRuleList;
    }

    public static Builder builder(ShortResource resource) {
        return new Builder(resource);
    }

    public static class Builder {
        private final ShortResource shortResource;
        public ShortAttributeList attributeList;
        public SecurityRuleList securityRuleList;

        private Builder(ShortResource shortResource) {
            this.shortResource = shortResource;
        }

        public Builder withAttributes(ShortAttributeList attributeList) {
            this.attributeList = attributeList;
            return this;
        }

        public Builder withSecurityRules(SecurityRuleList securityRuleList) {
            this.securityRuleList = securityRuleList;
            return this;
        }

        public ExtShortResource build() {
            return new ExtShortResource(this);
        }
    }
}
