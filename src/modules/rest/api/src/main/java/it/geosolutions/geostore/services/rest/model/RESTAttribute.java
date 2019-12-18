package it.geosolutions.geostore.services.rest.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import it.geosolutions.geostore.services.dto.ShortAttribute;

/**
 * DTO for attribute object
 * @author Lorenzo Natali, GeoSolutions s.a.s.
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class RESTAttribute extends ShortAttribute {

	private static final long serialVersionUID = 1L;

}
