/* ====================================================================
 *
 * Copyright (C) 2017 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.ws.rs.core.SecurityContext;

import org.springframework.beans.factory.annotation.Autowired;


import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserSessionService;
import it.geosolutions.geostore.services.dto.UserSession;
import it.geosolutions.geostore.services.dto.UserSessionImpl;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.model.SessionToken;

public class RESTSessionServiceImpl extends RESTServiceImpl implements RESTSessionService{
	private static final String BEARER_TYPE = "bearer";
	@Autowired
	UserSessionService userSessionService;
	private boolean autorefresh = false;
	
	public boolean isAutorefresh() {
		return autorefresh;
	}

	public void setAutorefresh(boolean autorefresh) {
		this.autorefresh = autorefresh;
	}

	private long sessionTimeout = 86400; // 1 day

	public UserSessionService getUserSessionService() {
		return userSessionService;
	}

	public void setUserSessionService(UserSessionService userSessionService) {
		this.userSessionService = userSessionService;
	}

	private static SimpleDateFormat expireParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	/**
	 * Gets the User object associated to the given sessionId (if it exists).
	 * 
	 * @param sessionId
	 * @return
	 */
	public User getUser(String sessionId, boolean refresh) {
		User details = userSessionService.getUserData(sessionId);
		if (details != null && refresh && autorefresh) {
			userSessionService.refreshSession(sessionId, userSessionService.getRefreshToken(sessionId));
		}
		return details;
	}

	/**
	 * Gets the username associated to the given sessionId (if it exists).
	 * 
	 * @param sessionId
	 * @return
	 */
	public String getUserName(String sessionId, boolean refresh) {
		User userData = userSessionService.getUserData(sessionId);
		if (userData != null) {
			if (refresh  && autorefresh) {
				userSessionService.refreshSession(sessionId, userSessionService.getRefreshToken(sessionId));
			}
			return userData.getName();
		}
		return null;
	}

	private Calendar getExpiration(String expires) throws ParseException {
		if (!"".equals(expires)) {
			return toCalendar(expires);
		}
		return null;
	}

	/**
	 * Creates a new session for the User in SecurityContext.
	 * 
	 * @return
	 * @throws ParseException
	 */
	public String createSession(String expires,SecurityContext sc) throws ParseException {
		User user = extractAuthUser(sc);
		if (user != null) {
			Calendar expiration = getExpiration(expires);
			UserSession session = null;
			if (user instanceof User) {
				session = new UserSessionImpl(null, user, expiration);
			}
			return userSessionService.registerNewSession(session);
		}

		return null;
	}
	
	@Override
	public SessionToken login(SecurityContext sc) throws ParseException {
		Calendar expires = new GregorianCalendar();
		expires.add(Calendar.SECOND, (int) getSessionTimeout());
		User user = extractAuthUser(sc);
		if (user != null) {
			
			UserSession session = null;
			if (user instanceof User) {
				session = new UserSessionImpl(null, user, expires);
				session.setExpirationInterval(getSessionTimeout());
			}
			return toSessionToken(userSessionService.registerNewSession(session), session);
		}
		return null;
	}

	private SessionToken toSessionToken(String accessToken, UserSession sessionToken) {
		if(sessionToken == null) {
			return null;
		}
		SessionToken token = new SessionToken();
		token.setAccessToken(accessToken);
		token.setRefreshToken(sessionToken.getRefreshToken());
		token.setExpires(sessionToken.getExpirationInterval());
		token.setTokenType(BEARER_TYPE);
		return token;
	}

	@Override
	public SessionToken refresh(SecurityContext sc, String sessionId, String refreshToken) {
		return toSessionToken(sessionId, userSessionService.refreshSession(sessionId, refreshToken));

	}

	/**
	 * Removes the given session.
	 * 
	 * @return
	 */
	public void removeSession(String sessionId) {
		userSessionService.removeSession(sessionId);
	}

	/**
	 * Removes all sessions.
	 * 
	 * @return
	 */
	public void clear() {
		userSessionService.removeAllSessions();
	}

	/** Transform Calendar to ISO 8601 string. */
	public static String fromCalendar(final Calendar calendar) {
		Date date = calendar.getTime();
		String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);
		return formatted.substring(0, 22) + ":" + formatted.substring(22);
	}

	/** Transform ISO 8601 string to Calendar. */
	public static Calendar toCalendar(final String iso8601string) throws ParseException {
		Calendar calendar = GregorianCalendar.getInstance();
		String s = iso8601string.replace("Z", "+00:00");
		try {
			s = s.substring(0, 22) + s.substring(23); // to get rid of the ":"
		} catch (IndexOutOfBoundsException e) {
			throw new ParseException("Invalid length", 0);
		}
		Date date = expireParser.parse(s);
		calendar.setTime(date);
		return calendar;
	}

	
	@Override
	protected SecurityService getSecurityService() {
		return null;
	}

	public long getSessionTimeout() {
		return sessionTimeout;
	}

	public void setSessionTimeout(long sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}

	

}
