/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2023 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2023 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.assets.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opennms.core.logging.Logging;
import org.opennms.core.utils.StringUtils;
import org.opennms.core.sysprops.SystemProperties;
import org.opennms.web.assets.api.AssetLocator;
import org.opennms.web.assets.api.AssetResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

public class AssetLocatorImpl extends AbstractResourceResolver implements AssetLocator, InitializingBean {
    private static Logger LOG = LoggerFactory.getLogger(AssetLocatorImpl.class);

    private static AssetLocator s_instance;
    private static final Resource s_assetsPath = new ClassPathResource("/assets/");

    private ScheduledExecutorService m_executor = null;

    private Map<String,List<AssetResource>> m_unminified = new HashMap<>();
    private Map<String,List<AssetResource>> m_minified   = new HashMap<>();
    long m_lastModified = 0;
    long m_reload;
    String m_filesystemPath;
    boolean m_useMinified;

    public static AssetLocator getInstance() {
        return s_instance;
    }

    public AssetLocatorImpl() {
        m_filesystemPath = System.getProperty("org.opennms.web.assets.path");
        m_useMinified = Boolean.parseBoolean(System.getProperty("org.opennms.web.assets.minified", "true"));
        m_reload = SystemProperties.getLong("org.opennms.web.assets.reload", 5000l);
    }

    @Override
    public long lastModified() {
        return m_lastModified;
    }

    @Override
    public Collection<String> getAssets() {
        return getAssets(m_useMinified);
    }

    @Override
    public Collection<String> getAssets(final boolean minified) {
        return minified? m_minified.keySet() : m_unminified.keySet();
    }

    @Override
    public Optional<Collection<AssetResource>> getResources(final String assetName) {
        return getResources(assetName, m_useMinified);
    }

    @Override
    public Optional<Collection<AssetResource>> getResources(final String assetName, final boolean minified) {
        return Optional.ofNullable(minified? m_minified.get(assetName) : m_unminified.get(assetName));
    }

    @Override
    public Optional<AssetResource> getResource(final String assetName, final String type) {
        return getResource(assetName, type, m_useMinified);
    }

    @Override
    public Optional<AssetResource> getResource(final String assetName, final String type, final boolean minified) {
        final Optional<Collection<AssetResource>> resources = getResources(assetName, minified);
        if (resources.isPresent()) {
            final Collection<AssetResource> r = resources.get();
            return r.parallelStream().filter(resource -> {
                return type.equals(resource.getType());
            }).findFirst();
        }
        return Optional.empty();
    }

    @Override
    public Optional<InputStream> open(final String assetName, final String type) throws IOException {
        return open(assetName, type, m_useMinified);
    }

    @Override
    public Optional<InputStream> open(final String assetName, final String type, final boolean minified) throws IOException {
        return withLogPrefix(() -> {
            final Optional<AssetResource> r = getResource(assetName, type, minified);
            if (!r.isPresent()) {
                LOG.info("Unable to locate asset resource {}:{}", assetName, type);
                return Optional.empty();
            }
            final AssetResource resource = r.get();

            if (m_filesystemPath != null) {
                final Path p = Paths.get(m_filesystemPath).resolve(resource.getPath());
                LOG.debug("assets path is set, attempting to load {}:{} from {}", assetName, type, p);
                if (p.toFile().exists()) {
                    return Optional.of(new FileInputStream(p.toFile()));
                }
            }

            final String resourcePath = resource.getPath();
            LOG.debug("Opening resource {} for asset {}", resourcePath, r);
            final URL url = getClass().getResource(resourcePath);
            if (url != null) {
                return Optional.of(url.openStream());
            }
            return Optional.of(new ClassPathResource(resourcePath).getInputStream());
        });
    }

    @Override
    public void reload() {
        final Map<String,List<AssetResource>> minified = loadAssets(true);
        final Map<String,List<AssetResource>> unminified = loadAssets(false);

        if (minified != null) {
            m_minified = minified;
        }
        if (unminified != null) {
            m_unminified = unminified;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (m_reload > 0) {
            m_executor = Executors.newSingleThreadScheduledExecutor();
            m_executor.scheduleAtFixedRate(() -> {
                reload();
            }, m_reload, m_reload, TimeUnit.MILLISECONDS);
        }

        // always load at least once
        reload();
        // make it easier to reach from JSP pages
        s_instance = this;
    }

    public long getReloadMinutes() {
        return m_reload;
    }
    
    public void setReloadMinutes(final long minutes) {
        m_reload = minutes;
    }
    
    public boolean getUseMinified() {
        return m_useMinified;
    }
    
    public void setUseMinified(final boolean minified) {
        m_useMinified=  minified;
    }

    private Map<String,List<AssetResource>> loadAssets(final boolean minified) {
        return withLogPrefix(() ->  {
            try {
                final Map<String,List<AssetResource>> newAssets = new HashMap<>();

                Resource r = new ClassPathResource(minified? "/assets/assets.min.json" : "/assets/assets.json");

                if (m_filesystemPath != null) {
                    final Path p = Paths.get(m_filesystemPath).resolve(minified? "assets.min.json" : "assets.json");
                    if (p.toFile().exists()) {
                        r = new FileSystemResource(p.toFile());
                    }
                }

                LOG.info("Loading asset data from {}", r);
                byte[] bdata = FileCopyUtils.copyToByteArray(r.getInputStream());

                final String json = new String(bdata, StandardCharsets.UTF_8);
                final JSONObject assetsObj = new JSONObject(json);
                final JSONArray names = assetsObj.names();
                for (int i=0; i < names.length(); i++) {
                    final String assetName = names.getString(i);
                    final JSONObject assetObj = assetsObj.getJSONObject(assetName);
                    final List<AssetResource> assets = new ArrayList<>(assetObj.length());
                    final JSONArray keys = assetObj.names();
                    int count = 0;
                    for (int j=0; j < keys.length(); j++) {
                        final String type = keys.getString(j);
                        if (!assetObj.isNull(type)) {
                            final Object item = assetObj.get(type);
                            if (item instanceof JSONArray) {
                                LOG.debug("{} is an anonymous type resource; skipping indexing", type);
                            } else {
                                final String path = assetObj.getString(type);
                                assets.add(new AssetResource(assetName, type, path));
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        newAssets.put(assetName, assets);
                    }
                }

                m_lastModified = Math.max(getLastModified(), r.lastModified());
                return newAssets;
            } catch (final Exception e) {
                LOG.warn("Failed to load asset manifest.", e);
            }
            return null;
        });
    }

    private long getLastModified() {
        long lastModified = 0;
        if (m_filesystemPath != null) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(m_filesystemPath))) {
                for (final Path path : stream) {
                    lastModified = Math.max(path.toFile().lastModified(), lastModified);
                }
            } catch (final IOException e) {
                LOG.warn("Failed to scan {} for modified files.", m_filesystemPath);
            }
        }
        return lastModified;
    }

    @Override
    protected Resource resolveResourceInternal(final HttpServletRequest request, final String requestPath, final List<? extends Resource> locations, final ResourceResolverChain chain) {
        return getResource(requestPath, locations);
    }

    protected Resource getResource(final String requestPath, final List<? extends Resource> locations) {
        return withLogPrefix(() -> {
            for (final Resource location : locations) {
                try {
                    if (resourcesMatch(s_assetsPath, location)) {
                        final Resource resource = location.createRelative(requestPath);
                        LOG.debug("checking request {} in location {}", requestPath, location);
                        final String fileName = resource.getFilename();

                        if (m_filesystemPath != null) {
                            final File f = Paths.get(m_filesystemPath, fileName).toFile();
                            LOG.debug("Checking for resource in filesystem: {}", f);
                            if (f.exists() && f.canRead()) {
                                LOG.trace("File exists and is readable: {}", f);
                                return new FileSystemResource(f);
                            }
                        }

                        final int index = fileName.lastIndexOf(".");
                        if (index > 0) {
                            final String assetName = fileName.substring(0,  index);
                            final String type = fileName.substring(index + 1);
                            final Optional<AssetResource> assetResource = getResource(assetName, type);
                            LOG.debug("Checking for resource in classpath: {}.{} ({})", assetName, type, assetResource);
                            if (assetResource.isPresent()) {
                                final Resource relativeResource = new ClassPathResource("/" + assetResource.get().getPath());
                                LOG.debug("Using ClassPathResource: {}", relativeResource);
                                if (relativeResource.exists() && relativeResource.isReadable()) {
                                    LOG.trace("Resource exists and is readable: {}", relativeResource);
                                    return relativeResource;
                                }
                            } else {
                                LOG.debug("Asset resource was not found: {}:{}", assetName, type);
                            }
                        }

                        if (resource.exists()) {
                            return resource;
                        }
                    }

                    LOG.debug("unhandled location {} for request path {}", location, requestPath);
                } catch (final IOException e) {
                    LOG.debug("Failed to create relative path from {} in {}. Trying next location.", requestPath, location, e);
                }
            }
            return null;
        });
    }

    private boolean resourcesMatch(final Resource a, final Resource b) {
        final String aPath = getPath(a);
        final String bPath = getPath(b);
        if (aPath == null || bPath == null) {
            return false;
        }
        return aPath.equals(bPath);
    }

    @SuppressWarnings("java:S2259") // sonar doesn't know that StringUtils.hasText null-checks
    private String getPath(final Resource resource) {
        String ret = null;
        if (resource instanceof UrlResource) {
            try {
                ret = resource.getURL().toExternalForm();
            } catch (final IOException e) {
                return null;
            }
        } else if (resource instanceof ClassPathResource) {
            ret = ((ClassPathResource) resource).getPath();
        } else if (resource instanceof ServletContextResource) {
            ret = ((ServletContextResource) resource).getPath();
        }
        else {
            try {
                ret = resource.getURL().getPath();
            } catch (final IOException e) {
                return null;
            }
        }
        if (StringUtils.hasText(ret)) {
            return ret.startsWith("/")? ret.substring(1) : ret;
        }
        return null;
    }

    @Override
    protected String resolveUrlPathInternal(final String resourcePath, final List<? extends Resource> locations, final ResourceResolverChain chain) {
        return (StringUtils.hasText(resourcePath) && getResource(resourcePath, locations) != null ? resourcePath : null);
    }

    private <T>T withLogPrefix(final Callable<T> cb) {
        try {
            return Logging.withPrefix("web", cb);
        } catch (final Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
