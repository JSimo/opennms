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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.DatabaseSchema;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterParseException;

public class SqlEmitterTest {

    private List<Table> tables;
    private DatabaseSchemaConfig schemaConfig;
    private List<String> extractedStrings;

    @Before
    public void setUp() {
        tables = new ArrayList<>();
        schemaConfig = new MockSchemaConfig();
        extractedStrings = new ArrayList<>();
    }

    private String emit(Expr e) {
        return e.accept(new SqlEmitter(tables, schemaConfig, extractedStrings));
    }

    @Test
    public void catincEmitsInSubquery() throws FilterParseException {
        Expr e = new CatincExpr("Routers");
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue("category condition uses IN (subquery) for planner hash semi-join", sql.contains(" IN (SELECT"));
        assertTrue(sql.contains("category_node"));
        assertTrue(sql.contains("categories"));
        assertTrue(sql.contains("categoryName = 'Routers'"));
        assertTrue(sql.contains("node.nodeID"));
    }

    @Test
    public void catincInEmitsInSubquery() throws FilterParseException {
        Expr e = new CatincInExpr(List.of("A", "B"));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue("category condition uses IN (subquery)", sql.contains(" IN (SELECT"));
        assertTrue(sql.contains("categoryName IN ('A', 'B')"));
        assertTrue(sql.contains("node.nodeID"));
    }

    @Test
    public void orEmitsParenthesizedOr() throws FilterParseException {
        Expr e = new OrExpr(List.of(new CatincExpr("A"), new CatincExpr("B")));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains(" OR "));
        assertTrue(sql.contains(" IN (SELECT"));
    }

    @Test
    public void andEmitsParenthesizedAnd() throws FilterParseException {
        Expr e = new AndExpr(List.of(new CatincExpr("A"), new IsServiceExpr("ICMP")));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains(" AND "));
        assertTrue(sql.contains("serviceName = 'ICMP'"));
    }

    @Test
    public void optimizerMergesInAndNotInCategoryToCatincInExcept() throws FilterParseException {
        Expr ast = new AndExpr(List.of(
                new CatincInExpr(List.of("Production", "SWITCH")),
                new NotExpr(new CatincExpr("CUST")),
                new IsServiceInExpr(List.of("ICMP", "SNMP"))));
        Expr optimized = new FilterOptimizer().optimize(ast);
        String sql = emit(optimized);
        assertNotNull(sql);
        assertTrue("optimizer produces EXCEPT form; emitter emits it", sql.contains(" EXCEPT "));
        assertTrue(sql.contains("Production"));
        assertTrue(sql.contains("CUST"));
        assertTrue(sql.contains("serviceName"));
        assertFalse("single combined predicate, no separate NOT IN", sql.contains(" NOT IN (SELECT"));
    }

    @Test
    public void catincInExceptEmitsExceptSubquery() throws FilterParseException {
        Expr e = new CatincInExceptExpr(
                List.of(List.of("Production", "SWITCH")),
                List.of("CUST"));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains(" EXCEPT "));
        assertTrue(sql.contains("Production"));
        assertTrue(sql.contains("CUST"));
        assertTrue(sql.contains("node.nodeID"));
    }

    @Test
    public void catincNotInEmitsNotInSubquery() throws FilterParseException {
        Expr e = new CatincNotInExpr(List.of("DMZ"));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue("CatincNotInExpr emits NOT IN (subquery) for planner hash anti-join", sql.contains(" NOT IN (SELECT"));
        assertTrue(sql.contains("categoryName = 'DMZ'"));
        assertTrue(sql.contains("node.nodeID"));
    }

    @Test
    public void optimizerNormalizesNotCatincToCatincNotInThenEmitsNotIn() throws FilterParseException {
        Expr ast = new NotExpr(new CatincExpr("DMZ"));
        Expr optimized = new FilterOptimizer().optimize(ast);
        assertTrue(optimized instanceof CatincNotInExpr);
        String sql = emit(optimized);
        assertTrue(sql.contains(" NOT IN (SELECT"));
        assertTrue(sql.contains("DMZ"));
    }

    @Test
    public void isServiceEmitsServiceNameEq() throws FilterParseException {
        Expr e = new IsServiceExpr("ICMP");
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("serviceName = 'ICMP'"));
    }

    @Test
    public void isServiceInEmitsServiceNameIn() throws FilterParseException {
        Expr e = new IsServiceInExpr(List.of("ICMP", "SNMP"));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains(" IN "));
        assertTrue(sql.contains("serviceName"));
        assertTrue(sql.contains("'ICMP'"));
        assertTrue(sql.contains("'SNMP'"));
    }

    @Test
    public void catincAndInEmitsInSubqueryWithMultipleJoins() throws FilterParseException {
        Expr e = new CatincAndInExpr(List.of(List.of("A", "B"), List.of("C", "D")));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue("category AND uses IN (subquery)", sql.contains(" IN (SELECT"));
        assertTrue(sql.contains("cn1"));
        assertTrue(sql.contains("cn2"));
        assertTrue(sql.contains("c1.categoryName IN ('A', 'B')"));
        assertTrue(sql.contains("c2.categoryName IN ('C', 'D')"));
    }

    @Test
    public void notIsServiceEmitsNotInSubquery() throws FilterParseException {
        Expr e = new NotIsServiceExpr("HTTP");
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("NOT IN"));
        assertTrue(sql.contains("ifServices"));
        assertTrue(sql.contains("service.serviceName ='HTTP'"));
    }

    @Test
    public void columnRefEmitsResolvedColumn() throws FilterParseException {
        Expr e = new ColumnRefExpr("nodeLabel");
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("nodeLabel"));
    }

    @Test
    public void inEmitsInClause() throws FilterParseException {
        extractedStrings.add("'a'");
        extractedStrings.add("'b'");
        Expr e = new InExpr(new ColumnRefExpr("nodeLabel"), List.of(new PlaceholderExpr("0"), new PlaceholderExpr("1")));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains(" IN "));
        assertTrue(sql.contains("nodeLabel"));
        assertTrue(sql.contains("'a'"));
        assertTrue(sql.contains("'b'"));
    }

    @Test
    public void isNullEmitsIsNull() throws FilterParseException {
        Expr e = new IsNullExpr(new ColumnRefExpr("nodeLabel"), false);
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("nodeLabel"));
        assertTrue(sql.contains("IS NULL"));
        assertFalse(sql.contains("NOT"));
    }

    @Test
    public void isNotNullEmitsIsNotNull() throws FilterParseException {
        Expr e = new IsNullExpr(new ColumnRefExpr("nodeLabel"), true);
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("nodeLabel"));
        assertTrue(sql.contains("IS NOT NULL"));
    }

    @Test
    public void placeholderEmitsExtractedString() {
        extractedStrings.add("'Server'");
        Expr e = new PlaceholderExpr("0");
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("'Server'"));
    }

    @Test
    public void comparisonEmitsLeftOpRight() throws FilterParseException {
        extractedStrings.add("'myhost'");
        Expr e = new ComparisonExpr(
                new ColumnRefExpr("nodeLabel"),
                "=",
                new PlaceholderExpr("0"));
        String sql = emit(e);
        assertNotNull(sql);
        assertTrue(sql.contains("nodeLabel"));
        assertTrue(sql.contains("="));
        assertTrue(sql.contains("'myhost'"));
    }

    /**
     * Minimal mock that returns table.column for addColumn without loading schema XML.
     */
    private static class MockSchemaConfig implements DatabaseSchemaConfig {
        @Override
        public String addColumn(List<Table> tables, String column) {
            switch (column) {
                case "nodeID":
                    return "node.nodeID";
                case "serviceName":
                    return "service.serviceName";
                case "ipAddr":
                    return "ipInterface.ipAddr";
                default:
                    return "node." + column;
            }
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
            return Collections.emptyList();
        }

        @Override
        public String constructJoinExprForTables(List<Table> tables) {
            return "";
        }
    }
}
