/* ====================================================================
 *
 * Copyright (C) 2015 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.core.security;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Implementation of UserMapper that maps attributes from a UserDetailsWithAttributes
 * object to GeoStore User attributes. Mappings are expressed using SpEL expressions.
 * 
 * @author Mauro Bartolomeoli
 *
 */
public class ExpressionUserMapper implements UserMapper {
    Map<String, String> attributeMappings;
    
    public static SpelExpressionParser parser = new SpelExpressionParser();
    
    public static StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
    
    static {
        // property accessor for UserDetailsWithAttributes attributes (read only)
        evaluationContext.addPropertyAccessor(new PropertyAccessor() {

            @Override
            public void write(EvaluationContext ctx, Object target, String name, Object value)
                    throws AccessException {

            }

            @Override
            public TypedValue read(EvaluationContext ctx, Object target, String name)
                    throws AccessException {
                if (target instanceof UserDetailsWithAttributes) {
                    UserDetailsWithAttributes details = (UserDetailsWithAttributes) target;
                    return new TypedValue(details.getAttribute(name));
                }
                return null;
            }

            @Override
            public Class[] getSpecificTargetClasses() {
                return new Class[] { UserDetailsWithAttributes.class };
            }

            @Override
            public boolean canWrite(EvaluationContext ctx, Object target, String name)
                    throws AccessException {
                return false;
            }

            @Override
            public boolean canRead(EvaluationContext ctx, Object target, String name)
                    throws AccessException {
                return target instanceof UserDetailsWithAttributes;
            }
        });
    }

    /**
     * 
     * @param attributeMappings maps attribute names to SpEL expressions (using a UserDetailsWithAttributes
     * source)
     */
    public ExpressionUserMapper(Map<String, String> attributeMappings) {
        this.attributeMappings = attributeMappings;
    }
    
    @Override
    /**
     * If details is a UserDetailsWithAttributes object, its attributes are mapped to User
     * attributes.
     * Each mapping is an SpEL expression using the UserDetailsWithAttributes as a source.
     * 
     */
    public void mapUser(UserDetails details, User user) {
        List<UserAttribute> attributes = new ArrayList<UserAttribute>();
        for(String attributeName : attributeMappings.keySet()) {
            
            Expression exp = parser.parseExpression(attributeMappings.get(attributeName));
            UserAttribute userAttribute = new UserAttribute();
            userAttribute.setName(attributeName);
            Object value = exp.getValue(evaluationContext, details);
            userAttribute.setValue(value == null ? null : value.toString());
            attributes.add(userAttribute);
        }
        user.setAttribute(attributes);
        
    }

}
