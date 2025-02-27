/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.provision.detector.client.rpc;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.mate.api.FallbackScope;
import org.opennms.core.mate.api.Interpolator;
import org.opennms.core.mate.api.MetadataConstants;
import org.opennms.core.mate.api.Scope;
import org.opennms.core.rpc.api.RpcRequest;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.netmgt.provision.DetectRequest;
import org.opennms.netmgt.provision.DetectorRequestBuilder;
import org.opennms.netmgt.provision.PreDetectCallback;
import org.opennms.netmgt.provision.ServiceDetectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.opentracing.Span;

public class DetectorRequestBuilderImpl implements DetectorRequestBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DetectorRequestBuilderImpl.class);

    private static final String PORT = "port";

    private String location;

    private String systemId;

    private String className;

    private InetAddress address;

    private Integer nodeId;

    private Map<String, String> attributes = new HashMap<>();

    private Span span;

    private PreDetectCallback preDetectCallback;

    private final LocationAwareDetectorClientRpcImpl client;

    public DetectorRequestBuilderImpl(LocationAwareDetectorClientRpcImpl client) {
        this.client = client;
    }

    @Override
    public DetectorRequestBuilder withLocation(String location) {
        this.location = location;
        return this;
    }

    @Override
    public DetectorRequestBuilder withSystemId(String systemId) {
        this.systemId = systemId;
        return this;
    }

    @Override
    public DetectorRequestBuilder withClassName(String className) {
        this.className = className;
        return this;
    }

    @Override
    public DetectorRequestBuilder withServiceName(String serviceName) {
        String className = client.getRegistry().getDetectorClassNameFromServiceName(serviceName);
        if(className == null) {
            throw new IllegalArgumentException("No detector found with service name '" + serviceName + "'.");
        }
        this.className = className;
        return this;
    }

    @Override
    public DetectorRequestBuilder withAddress(InetAddress address) {
        this.address = address;
        return this;
    }

    @Override
    public DetectorRequestBuilder withAttributes(Map<String, String> attributes) {
        this.attributes.putAll(attributes);
        return this;
    }

    @Override
    public DetectorRequestBuilder withAttribute(String key, String value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public DetectorRequestBuilder withNodeId(Integer nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    @Override
    public DetectorRequestBuilder withParentSpan(Span span) {
        this.span = span;
        return this;
    }

    @Override
    public DetectorRequestBuilder withPreDetectCallback(PreDetectCallback preDetectCallback) {
        this.preDetectCallback = preDetectCallback;
        return this;
    }

    /**
     * Builds the {@link DetectorRequestDTO} and executes the requested detector
     * via the RPC client.
     */
    @Override
    public CompletableFuture<Boolean> execute() {
        if (address == null) {
            throw new IllegalArgumentException("Address is required.");
        } else if (className == null) {
            throw new IllegalArgumentException("Detector class name is required.");
        }

        final Scope scope = new FallbackScope(
                this.client.getEntityScopeProvider().getScopeForNode(nodeId),
                this.client.getEntityScopeProvider().getScopeForInterface(nodeId, InetAddressUtils.toIpAddrString(address))
        );

        final Map<String, String> interpolatedAttributes = Interpolator.interpolateStrings(attributes, scope);

        // Retrieve the factory associated with the requested detector
        final ServiceDetectorFactory<?> factory = client.getRegistry().getDetectorFactoryByClassName(className);
        if (factory == null) {
            // Fail immediately if no suitable factory was found
            throw new IllegalArgumentException("No factory found for detector with class name '" + className + "'.");
        }

        // Store all of the request details in the DTO
        final DetectorRequestDTO detectorRequestDTO = new DetectorRequestDTO();
        detectorRequestDTO.setLocation(location);
        detectorRequestDTO.setSystemId(systemId);
        detectorRequestDTO.setClassName(className);
        detectorRequestDTO.setAddress(address);
        // Update ttl from metadata
        String timeToLive = interpolatedAttributes.get(MetadataConstants.TTL);
        if (!Strings.isNullOrEmpty(timeToLive)) {
            Long ttlFromMetadata = ParameterMap.getLongValue(MetadataConstants.TTL, interpolatedAttributes.get(MetadataConstants.TTL), null);
            detectorRequestDTO.setTimeToLiveMs(ttlFromMetadata);
            //Remove ttl from attributes as it is not a detector attribute.
            interpolatedAttributes.remove(MetadataConstants.TTL);
        }
        detectorRequestDTO.addDetectorAttributes(interpolatedAttributes);
        detectorRequestDTO.addTracingInfo(RpcRequest.TAG_CLASS_NAME, className);
        detectorRequestDTO.addTracingInfo(RpcRequest.TAG_IP_ADDRESS, InetAddressUtils.toIpAddrString(address));
        detectorRequestDTO.setSpan(span);
        detectorRequestDTO.setPreDetectCallback(preDetectCallback);
        // Attempt to extract the port from the list of attributes
        Integer port = null;
        final String portString = interpolatedAttributes.get(PORT);
        if (portString != null) {
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException nfe) {
                LOG.warn("Failed to parse port as integer from: ", portString);
            }
        }

        // Build the DetectRequest and store the runtime attributes in the DTO
        final DetectRequest request = factory.buildRequest(location, address, port, interpolatedAttributes);
        detectorRequestDTO.addRuntimeAttributes(Interpolator.interpolateStrings(request.getRuntimeAttributes(), scope));

        // Execute the request
        return client.getDelegate().execute(detectorRequestDTO)
            .thenApply(response -> {
                // Notify the factory that a request was successfully executed
                try {
                    factory.afterDetect(request, response, nodeId);
                } catch (Throwable t) {
                    LOG.error("Error while processing detect callback.", t);
                }
                return response.isDetected();
            });
    }
}
