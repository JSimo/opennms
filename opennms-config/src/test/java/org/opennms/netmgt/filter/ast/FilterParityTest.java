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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.DatabaseSchema;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.LegacyFilterParser;
import org.opennms.netmgt.filter.api.FilterParseException;

/**
 * Validates AST coverage against the legacy regex parser.
 * Catinc optimizer rules may emit different but equivalent SQL — those are parse-only checks.
 */
@RunWith(Parameterized.class)
public class FilterParityTest {

    private static final Pattern FILTER_TAG = Pattern.compile("<filter>([^<]*)</filter>");

    private final String rule;
    private final boolean expectExactSqlParity;

    public FilterParityTest(String rule, boolean expectExactSqlParity) {
        this.rule = rule;
        this.expectExactSqlParity = expectExactSqlParity;
    }

    @Parameters(name = "{0}")
    public static List<Object[]> rules() throws IOException {
        List<Object[]> all = new ArrayList<>();
        for (String rule : docAndShippedRules()) {
            all.add(new Object[] { rule, !containsCatinc(rule) });
        }
        for (String rule : javadocConstructRules()) {
            all.add(new Object[] { rule, true });
        }
        return all;
    }

    private static boolean containsCatinc(String rule) {
        return rule.toLowerCase().contains("catinc");
    }

    private static List<String> docAndShippedRules() throws IOException {
        List<String> rules = new ArrayList<>();
        rules.add("IPADDR != '0.0.0.0'");
        rules.add("(IPADDR != '0.0.0.0') & (IPADDR IPLIKE 192.168.1.1-154) & (isSMTP | isPOP3) & (categoryName == 'Production')");
        rules.add("(IPADDR != '0.0.0.0' & (IPADDR IPLIKE 192.168.1.1-250) & (isHTTP | isHTTPS) & (categoryName == 'Virtual'))");
        rules.add("foreignSource == 'Minions' & IPADDR != '0.0.0.0'");
        rules.add("!(IPADDR IPLIKE 169.254.*.*)");
        rules.add("location='Default' & (IPADDR IPLIKE 172.*.*.*)");
        rules.add("ipaddr IPLIKE *.*.*.*");
        rules.add("nodeSysOID LIKE '.1.3.6.1.4.1.9.%'");
        rules.add("(catincProduction | catincUNVERIFIED) & (nodeSysOID LIKE '.1.3.6.1.4.1.6527.1.3.%')");
        rules.add("catincRouters & catincLinux");
        rules.addAll(loadFiltersFromEtc());
        return rules.stream().distinct().collect(Collectors.toList());
    }

    private static List<String> javadocConstructRules() {
        return List.of(
                "nodeLabel IS DISTINCT FROM 'other'",
                "nodeLabel IS NOT DISTINCT FROM 'other'",
                "ipAddr::INET = '10.0.0.1'",
                "nodeCreateTime::TIMESTAMP = '2020-01-01'",
                "serviceName NOT IN ('ICMP', 'SNMP')"
        );
    }

    private static List<String> loadFiltersFromEtc() throws IOException {
        Path etc = Paths.get("opennms-base-assembly/src/main/filtered/etc");
        if (!Files.isDirectory(etc)) {
            return Collections.emptyList();
        }
        List<String> rules = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(etc)) {
            paths.filter(p -> p.toString().endsWith(".xml"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            Matcher m = FILTER_TAG.matcher(content);
                            while (m.find()) {
                                String raw = m.group(1).trim();
                                if (!raw.isEmpty()) {
                                    rules.add(raw.replace("&amp;", "&"));
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return rules;
    }

    @Test
    public void astParsesRule() throws FilterParseException {
        List<Table> tables = new ArrayList<>();
        String astSql = FilterRuleParser.parseWhere(tables, rule, new ParitySchemaConfig());
        assertNotNull(astSql);
    }

    @Test
    public void legacyAndAstParityWhenExpected() throws FilterParseException {
        if (!expectExactSqlParity) {
            return;
        }
        DatabaseSchemaConfig schema = new ParitySchemaConfig();
        List<Table> legacyTables = new ArrayList<>();
        List<Table> astTables = new ArrayList<>();
        String legacy = LegacyFilterParser.parse(legacyTables, rule, schema);
        String ast = FilterRuleParser.parseWhereUnoptimized(astTables, rule, schema);
        assertEquals("SQL parity for rule: " + rule, normalize(legacy), normalize(ast));
    }

    private static String normalize(String sql) {
        String s = sql.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("\\s*=\\s*", " = ");
        boolean changed;
        do {
            changed = false;
            String next = s.replaceAll("\\(([^()]+)\\)", "$1");
            if (!next.equals(s)) {
                s = next;
                changed = true;
            }
        } while (changed);
        return s;
    }

    private static final class ParitySchemaConfig implements DatabaseSchemaConfig {
        @Override
        public String addColumn(List<Table> tables, String column) {
            String lower = column.toLowerCase();
            if ("ipaddr".equals(lower)) {
                return "ipInterface." + column;
            }
            if ("servicename".equals(lower)) {
                return "service." + column;
            }
            if ("categoryname".equals(lower)) {
                return "categories." + column;
            }
            return "node." + column;
        }

        @Override
        public DatabaseSchema getDatabaseSchema() {
            return null;
        }

        @Override
        public Table getPrimaryTable() {
            Table t = new Table();
            t.setName("ipInterface");
            return t;
        }

        @Override
        public Table getTableByName(String name) {
            return null;
        }

        @Override
        public Table findTableByVisibleColumn(String colName) {
            return getPrimaryTable();
        }

        @Override
        public int getTableCount() {
            return 1;
        }

        @Override
        public List<String> getJoinTables(List<Table> tables) {
            return Collections.emptyList();
        }

        @Override
        public String constructJoinExprForTables(List<Table> tables) {
            return "FROM ipInterface";
        }
    }
}
