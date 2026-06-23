/* ====================================================================
 *
 * Copyright (C) 2026 GeoSolutions S.r.l.
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
package it.geosolutions.geostore.services.rest.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import javax.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression test for {@link HeaderWriterFilterEagerConfig} (the CVE-2026-22732 mitigation).
 *
 * <p>Spring Security's {@code HeaderWriterFilter} writes security headers lazily by default, so a
 * response committed before the filter chain unwinds (e.g. a controller writing to the output
 * stream) silently loses them. The {@link HeaderWriterFilterEagerConfig} {@code BeanPostProcessor}
 * flips {@code shouldWriteHeadersEagerly} to {@code true}. These tests pin both the post-processor
 * logic and the fact that it reaches the {@code HeaderWriterFilter} created by the {@code
 * <security:http>} namespace (the way it is wired in {@code geostore-spring-security.xml}); if a
 * future security-XML refactor stopped the filter from being post-processed, the mitigation would
 * silently regress and this test would catch it.
 */
class HeaderWriterFilterEagerConfigTest {

    private static boolean isEager(HeaderWriterFilter filter) {
        return (boolean) ReflectionTestUtils.getField(filter, "shouldWriteHeadersEagerly");
    }

    private static HeaderWriterFilter findHeaderWriterFilter(FilterChainProxy proxy) {
        for (SecurityFilterChain chain : proxy.getFilterChains()) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof HeaderWriterFilter) {
                    return (HeaderWriterFilter) filter;
                }
            }
        }
        return null;
    }

    @Test
    void bppEnablesEagerWritingOnHeaderWriterFilterOnly() {
        HeaderWriterFilterEagerConfig bpp = new HeaderWriterFilterEagerConfig();

        HeaderWriterFilter filter =
                new HeaderWriterFilter(
                        Collections.singletonList(new StaticHeadersWriter("X-Test", "v")));
        assertFalse(isEager(filter), "the framework default must be lazy");

        Object returned = bpp.postProcessAfterInitialization(filter, "headerWriterFilter");
        assertSame(filter, returned, "the post-processor must return the same instance");
        assertTrue(isEager(filter), "the post-processor must enable eager header writing");

        Object unrelated = new Object();
        assertSame(
                unrelated,
                bpp.postProcessAfterInitialization(unrelated, "x"),
                "non-HeaderWriterFilter beans must be left untouched");
    }

    @Test
    void namespaceFilterIsEagerWhenBppPresent() {
        try (ClassPathXmlApplicationContext ctx =
                new ClassPathXmlApplicationContext("header-writer-eager-context.xml")) {
            HeaderWriterFilter filter = findHeaderWriterFilter(ctx.getBean(FilterChainProxy.class));
            assertNotNull(filter, "the <http> chain must contain a HeaderWriterFilter");
            assertTrue(
                    isEager(filter),
                    "the namespace HeaderWriterFilter must be eager (CVE-2026-22732 mitigation)");
        }
    }

    @Test
    void namespaceFilterStaysLazyWithoutBpp() {
        try (ClassPathXmlApplicationContext ctx =
                new ClassPathXmlApplicationContext("header-writer-noeager-context.xml")) {
            HeaderWriterFilter filter = findHeaderWriterFilter(ctx.getBean(FilterChainProxy.class));
            assertNotNull(filter);
            assertFalse(
                    isEager(filter),
                    "control: without the post-processor the filter keeps the lazy default");
        }
    }
}
