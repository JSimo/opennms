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
package org.opennms.netmgt.filter;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public class JdbcFilterDaoIpSubqueryTest {

    @Test
    public void fromClauseRewrittenWhenFilteringByAddress() {
        final String primaryTableName = "ipInterface";
        final String fromPrimary = "FROM " + primaryTableName + " ";
        final String fromSubquery = "FROM (SELECT * FROM " + primaryTableName + " WHERE ipaddr = ?) " + primaryTableName + " ";
        String sql = "SELECT DISTINCT ipInterface.ipAddr FROM ipInterface JOIN node ON (ipInterface.nodeID = node.nodeID) WHERE ipInterface.ipAddr = '1.2.3.4'";
        sql = sql.replaceFirst(Pattern.quote(fromPrimary), fromSubquery);
        assertTrue(sql.contains("FROM (SELECT * FROM ipInterface WHERE ipaddr = ?) ipInterface"));
        assertTrue(!sql.contains("AND ipInterface.ipaddr = ?"));
    }
}
