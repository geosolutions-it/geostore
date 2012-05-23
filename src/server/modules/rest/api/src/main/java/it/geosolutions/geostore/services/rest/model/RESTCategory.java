package it.geosolutions.geostore.services.rest.model;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Category")
public class RESTCategory implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -3004145977232782933L;
	
    /** The id. */
	private Long id;
	
	private String name;

    public RESTCategory() {
    }

    public RESTCategory(Long id) {
        this.id = id;
    }

    public RESTCategory(String name) {
        this.name = name;
    }

	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {		
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName()).append('[');

        if (id != null){
        	builder.append("id=").append(id);
        }

        if (name != null){
        	builder.append(", ");
        	builder.append("name=").append(name);
        }

        builder.append(']');
        return builder.toString();
	}

}
