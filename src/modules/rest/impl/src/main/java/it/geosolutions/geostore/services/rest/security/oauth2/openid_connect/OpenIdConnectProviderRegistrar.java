/* ====================================================================
 *
 * Copyright (C) 2024 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.oauth2.openid_connect;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Dynamically registers {@link OpenIdConnectConfiguration} beans for each provider declared in
 * {@code oidc.providers}. If the property is not set, defaults to {@code "oidc"} for backward
 * compatibility.
 *
 * <p>For each provider name {@code P}, registers:
 *
 * <ul>
 *   <li>{@code POAuth2Config} &rarr; {@link OpenIdConnectConfiguration} singleton
 * </ul>
 *
 * <p>The {@link org.springframework.beans.factory.config.PropertyOverrideConfigurer} fills
 * properties via {@code POAuth2Config.clientId=...} etc. Filters, validators, and rest templates
 * are created programmatically by the {@link CompositeOpenIdConnectFilter}.
 */
public class OpenIdConnectProviderRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdConnectProviderRegistrar.class);

    public static final String PROVIDERS_PROPERTY = "oidc.providers";
    private static final String DEFAULT_PROVIDER = "oidc";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
            throws BeansException {
        String providersValue =
                environment != null ? environment.getProperty(PROVIDERS_PROPERTY) : null;
        if (providersValue == null || providersValue.trim().isEmpty()) {
            providersValue = DEFAULT_PROVIDER;
        }

        String[] providers = providersValue.split(",");
        for (String raw : providers) {
            String provider = raw.trim();
            if (provider.isEmpty()) continue;

            String configBeanName = provider + CONFIG_NAME_SUFFIX;

            if (!registry.containsBeanDefinition(configBeanName)) {
                BeanDefinitionBuilder configBuilder =
                        BeanDefinitionBuilder.genericBeanDefinition(
                                OpenIdConnectConfiguration.class);
                registry.registerBeanDefinition(configBeanName, configBuilder.getBeanDefinition());
                LOGGER.info("Registered OpenIdConnectConfiguration bean: {}", configBeanName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // No post-processing needed on the bean factory itself
    }

    /** Returns the provider names as configured, defaulting to "oidc". */
    public String[] getProviderNames() {
        String providersValue =
                environment != null ? environment.getProperty(PROVIDERS_PROPERTY) : null;
        if (providersValue == null || providersValue.trim().isEmpty()) {
            return new String[] {DEFAULT_PROVIDER};
        }
        String[] names = providersValue.split(",");
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].trim();
        }
        return names;
    }
}
