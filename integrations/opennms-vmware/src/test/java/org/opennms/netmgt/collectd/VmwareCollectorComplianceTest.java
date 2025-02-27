/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.collectd;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.opennms.netmgt.collection.test.api.CollectorComplianceTest;
import org.opennms.netmgt.config.vmware.VmwareServer;
import org.opennms.netmgt.config.vmware.vijava.VmwareCollection;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.mock.MockTransactionTemplate;
import org.opennms.netmgt.dao.vmware.VmwareConfigDao;
import org.opennms.netmgt.dao.vmware.VmwareDatacollectionConfigDao;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.provision.service.vmware.VmwareImporter;
import org.opennms.netmgt.rrd.RrdRepository;
import org.opennms.netmgt.snmp.InetAddrUtils;

import com.google.common.collect.ImmutableMap;

public class VmwareCollectorComplianceTest extends CollectorComplianceTest {

    private static final String COLLECTION = "default";

    public VmwareCollectorComplianceTest() {
        super(VmwareCollector.class, true);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    @Override
    public Map<String, Object> getRequiredParameters() {
        return new ImmutableMap.Builder<String, Object>()
            .put("collection", COLLECTION)
            .build();
    }

    @Override
    public Map<String, Object> getRequiredBeans() {
        final OnmsNode node = mock(OnmsNode.class, RETURNS_DEEP_STUBS);
        final NodeDao nodeDao = mock(NodeDao.class);
        final MockTransactionTemplate mockTransactionTemplate = new MockTransactionTemplate();
        mockTransactionTemplate.afterPropertiesSet();
        when(nodeDao.get(anyInt())).thenReturn(node);

        when(node.findMetaDataForContextAndKey(VmwareImporter.METADATA_CONTEXT, VmwareImporter.METADATA_MANAGEMENT_SERVER)).thenReturn(Optional.of(new OnmsMetaData(VmwareImporter.METADATA_CONTEXT, "", "mdx")));
        when(node.findMetaDataForContextAndKey(VmwareImporter.METADATA_CONTEXT, VmwareImporter.METADATA_MANAGED_ENTITY_TYPE)).thenReturn(Optional.of(new OnmsMetaData(VmwareImporter.METADATA_CONTEXT, "", "tsx")));
        when(node.getForeignId()).thenReturn("rsx");

        VmwareCollection collection = new VmwareCollection();
        VmwareDatacollectionConfigDao vmwareDatacollectionConfigDao = mock(VmwareDatacollectionConfigDao.class);
        when(vmwareDatacollectionConfigDao.getVmwareCollection(COLLECTION)).thenReturn(collection);
        when(vmwareDatacollectionConfigDao.getRrdRepository(COLLECTION)).thenReturn(new RrdRepository());

        VmwareServer vmwareServer = new VmwareServer();
        vmwareServer.setHostname(InetAddrUtils.getLocalHostAddress().getCanonicalHostName());
        Map<String, VmwareServer> serverMap = new ImmutableMap.Builder<String, VmwareServer>()
            .put("mdx", vmwareServer)
            .build();

        VmwareConfigDao vmwareConfigDao = mock(VmwareConfigDao.class);
        when(vmwareConfigDao.getServerMap()).thenReturn(serverMap);

        return new ImmutableMap.Builder<String, Object>()
                .put("nodeDao", nodeDao)
                .put("vmwareDatacollectionConfigDao", vmwareDatacollectionConfigDao)
                .put("vmwareConfigDao", vmwareConfigDao)
                .put("transactionTemplate", mockTransactionTemplate)
                .build();
    }
}
