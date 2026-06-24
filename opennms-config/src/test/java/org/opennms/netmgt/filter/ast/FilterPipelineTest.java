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
package org.opennms.netmgt.filter.ast;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.DatabaseSchema;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterParseException;

public class FilterPipelineTest {

    @Test
    public void fullPipelineIplikeOperatorForm() throws FilterParseException {
        List<Table> tables = new ArrayList<>();
        String sql = FilterRuleParser.parseWhere(tables, "ipAddr IPLIKE 10.0.0.*", new MockSchema());
        assertTrue(sql.contains("IPLIKE(ipInterface.ipAddr, '10.0.0.*')"));
    }

    @Test
    public void fullPipelineNotIn() throws FilterParseException {
        List<Table> tables = new ArrayList<>();
        String sql = FilterRuleParser.parseWhere(tables, "serviceName NOT IN ('ICMP')", new MockSchema());
        assertTrue(sql.contains("service.serviceName NOT IN ('ICMP')"));
    }

    private static final class MockSchema implements DatabaseSchemaConfig {
        @Override
        public String addColumn(List<Table> tables, String column) {
            if ("ipAddr".equalsIgnoreCase(column)) {
                return "ipInterface.ipAddr";
            }
            if ("serviceName".equalsIgnoreCase(column)) {
                return "service.serviceName";
            }
            return "node." + column;
        }

        @Override
        public DatabaseSchema getDatabaseSchema() {
            return null;
        }

        @Override
        public Table getPrimaryTable() {
            return null;
        }

        @Override
        public Table getTableByName(String name) {
            return null;
        }

        @Override
        public Table findTableByVisibleColumn(String colName) {
            return null;
        }

        @Override
        public int getTableCount() {
            return 0;
        }

        @Override
        public List<String> getJoinTables(List<Table> tables) {
            return List.of();
        }

        @Override
        public String constructJoinExprForTables(List<Table> tables) {
            return "";
        }
    }
}
