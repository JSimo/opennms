/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.config.snmp;

import java.net.InetAddress;
import java.util.List;

/**
 * Cache for parsed IP addresses used during SNMP config lookups.
 * Implementations should use identity-based caching (keyed by object reference)
 * so that cache entries are automatically invalidated when config reloads
 * create new Definition/Range objects.
 */
public interface SnmpAddressCache {

    /**
     * Get parsed InetAddress list for a Definition's specifics.
     * @param def the Definition containing specific IP address strings
     * @return list of parsed InetAddress objects (never null)
     */
    List<InetAddress> getParsedSpecifics(Definition def);

    /**
     * Get parsed byte array for a Range's begin address.
     * @param range the Range containing the begin IP address string
     * @return byte array representation of the begin address
     */
    byte[] getParsedBegin(Range range);

    /**
     * Get parsed byte array for a Range's end address.
     * @param range the Range containing the end IP address string
     * @return byte array representation of the end address
     */
    byte[] getParsedEnd(Range range);
}
