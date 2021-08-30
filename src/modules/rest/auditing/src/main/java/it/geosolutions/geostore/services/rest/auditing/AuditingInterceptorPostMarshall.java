/* ====================================================================
 *
 * Copyright (C) 2007 - 2015 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.auditing;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.log4j.Logger;

public final class AuditingInterceptorPostMarshall extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOGGER = Logger.getLogger(AuditingInterceptorPostMarshall.class);

    public AuditingInterceptorPostMarshall() {
        super(Phase.POST_MARSHAL);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        Long responseLength = AuditInfoExtractor.getResponseLength(message);
        if (responseLength != null) {
            message.getExchange().put(AuditInfo.RESPONSE_LENGTH.getKey(), responseLength.toString());
        }
    }
}
