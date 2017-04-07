package it.geosolutions.geostore.services.rest.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement
public class SessionToken {
	String token_type;
	String access_token;
	String refresh_token;
	Long expires; 
	
	@XmlElement(name="token_type")
	public String getTokenType() {
		return token_type;
	}
	public void setTokenType(String token_type) {
		this.token_type = token_type;
	}
	@XmlElement(name="access_token")
	public String getAccessToken() {
		return access_token;
	}
	public void setAccessToken(String access_token) {
		this.access_token = access_token;
	}
	@XmlElement(name="expires")
	public Long getExpires() {
		return expires;
	}
	public void setExpires(Long expires) {
		this.expires = expires;
	}
	@XmlElement(name="refresh_token")
	public void setRefreshToken(String refresh_token) {
		this.refresh_token = refresh_token;
		
	}
	public String getRefreshToken() {
		return refresh_token;
	}
}
