/*
 *  Copyright (C) 2007 - 2025 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.services.rest.utils;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.IPRange;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.model.RESTIPRange;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.RESTSecurityRule;
import it.geosolutions.geostore.services.rest.model.RESTStoredData;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

/** @author ETj (etj at geo-solutions.it) */
public class Convert {

    public static Resource convertResource(RESTResource resource) {
        Category category = new Category();
        if (resource.getCategory().getName() != null)
            category.setName(resource.getCategory().getName());
        if (resource.getCategory().getId() != null) category.setId(resource.getCategory().getId());

        Resource r = new Resource();
        r.setDescription(resource.getDescription());
        r.setMetadata(resource.getMetadata());
        r.setName(resource.getName());
        r.setCategory(category);
        r.setCreator(resource.getCreator());
        r.setEditor(resource.getEditor());
        r.setAdvertised(resource.isAdvertised());

        // Parsing Attributes list
        if (CollectionUtils.isNotEmpty(resource.getAttribute())) {
            List<Attribute> attributes = Convert.convertAttributeList(resource.getAttribute());
            r.setAttribute(attributes);
        }

        RESTStoredData dataDto = resource.getStore();
        if (dataDto != null) {
            StoredData data = new StoredData();
            data.setData(dataDto.getData());

            r.setData(data);
        }

        return r;
    }

    public static List<Attribute> convertAttributeList(List<ShortAttribute> list)
            throws InternalErrorWebEx {
        List<Attribute> attributes = new ArrayList<Attribute>(list.size());
        for (ShortAttribute shortAttribute : list) {
            attributes.add(Convert.convertAttribute(shortAttribute));
        }
        return attributes;
    }

    public static Attribute convertAttribute(ShortAttribute shattr) throws InternalErrorWebEx {
        Attribute ret = new Attribute();
        ret.setName(shattr.getName());
        ret.setType(shattr.getType());

        if (shattr.getType() == null)
            throw new BadRequestWebEx("Missing type for attribute " + shattr);

        switch (ret.getType()) {
            case DATE:
                try {
                    ret.setDateValue(
                            new SimpleDateFormat(Attribute.DATE_FORMAT).parse(shattr.getValue()));
                } catch (ParseException e) {
                    throw new BadRequestWebEx("Error parsing attribute date value " + shattr);
                }
                break;
            case NUMBER:
                try {
                    ret.setNumberValue(Double.valueOf(shattr.getValue()));
                } catch (NumberFormatException ex) {
                    throw new BadRequestWebEx("Error parsing number value " + shattr);
                }
                break;
            case STRING:
                ret.setTextValue(shattr.getValue());
                break;
            default:
                throw new InternalErrorWebEx("Unknown attribute type " + shattr);
        }
        return ret;
    }

    public static List<ShortAttribute> convertToShortAttributeList(List<Attribute> list) {
        List<ShortAttribute> attributes = new ArrayList<ShortAttribute>(list.size());
        for (Attribute attr : list) {
            attributes.add(new ShortAttribute(attr));
        }
        return attributes;
    }

    public static List<SecurityRule> convertSecurityRuleList(
            List<RESTSecurityRule> list, Long resourceId) {
        if (list == null) {
            list = new ArrayList<>();
        }
        List<SecurityRule> rules = new ArrayList<>(list.size());
        for (RESTSecurityRule rule : list) {
            SecurityRule securityRule = new SecurityRule();
            Resource resource = new Resource();
            resource.setId(resourceId);
            securityRule.setResource(resource);

            if (rule.getUser() != null) {
                User user = new User();
                user.setId(rule.getUser().getId());
                user.setName(rule.getUser().getName());
                securityRule.setUser(user);
            }

            if (rule.getGroup() != null) {
                UserGroup group = new UserGroup();
                group.setId(rule.getGroup().getId());
                group.setGroupName(rule.getGroup().getGroupName());
                securityRule.setGroup(group);
            }

            if (rule.getIpRanges() != null) {
                securityRule.setIpRanges(
                        rule.getIpRanges().stream()
                                .map(Convert::convertIPRange)
                                .collect(Collectors.toSet()));
            }

            securityRule.setCanRead(rule.isCanRead());
            securityRule.setCanWrite(rule.isCanWrite());

            rules.add(securityRule);
        }
        return rules;
    }

    private static IPRange convertIPRange(RESTIPRange restipRange) {
        try {
            String cidr = restipRange.getCidr();
            System.out.println(cidr);

            IPRange ipRange = new IPRange();
            ipRange.setCidr(parseCidr(cidr));
            ipRange.setDescription(restipRange.getDescription());
            return ipRange;
        } catch (NumberFormatException ex) {
            throw new BadRequestWebEx("Error parsing IP range. Malformed IP address.");
        } catch (Exception ex) {
            throw new BadRequestWebEx("Error parsing IP range. " + ex.getMessage());
        }
    }

    private static String parseCidr(String cidr) throws AddressStringException {

        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format");
        }

        String ip = parts[0].trim();
        String prefix = parts[1].trim();

        String sanitizedIp =
                Arrays.stream(ip.split("\\."))
                        .map(s -> String.valueOf(Integer.parseInt(s)))
                        .collect(Collectors.joining("."));
        String sanitizedCidr = sanitizedIp + "/" + prefix;

        IPAddress parsedCidr = new IPAddressString(sanitizedCidr).toAddress();

        System.err.println(parsedCidr);

        return sanitizedCidr;
    }
}
