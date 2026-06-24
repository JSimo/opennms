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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterParseException;

/**
 * Emits SQL WHERE-clause fragments for the filter AST. Category conditions use
 * IN/NOT IN (subquery) so the planner can use hash semi/anti join.
 */
class SqlEmitter implements ExprVisitor<String> {

    private final List<Table> tables;
    private final DatabaseSchemaConfig schemaConfig;
    private final List<String> extractedStrings;

    public SqlEmitter(List<Table> tables, DatabaseSchemaConfig schemaConfig, List<String> extractedStrings) {
        this.tables = tables;
        this.schemaConfig = schemaConfig;
        this.extractedStrings = extractedStrings != null ? extractedStrings : List.of();
    }

    @Override
    public String visitAnd(AndExpr e) {
        return e.getChildren().stream()
                .map(c -> "(" + c.accept(this) + ")")
                .collect(Collectors.joining(" AND "));
    }

    @Override
    public String visitOr(OrExpr e) {
        return e.getChildren().stream()
                .map(c -> "(" + c.accept(this) + ")")
                .collect(Collectors.joining(" OR "));
    }

    @Override
    public String visitNot(NotExpr e) {
        return "NOT (" + e.getChild().accept(this) + ")";
    }

    @Override
    public String visitCatinc(CatincExpr e) throws FilterParseException {
        String nodeId = schemaConfig.addColumn(tables, "nodeID");
        return nodeId + " IN (" + CategorySubquery.forNames(List.of(e.getCategoryName())) + ")";
    }

    @Override
    public String visitCatincIn(CatincInExpr e) throws FilterParseException {
        String nodeId = schemaConfig.addColumn(tables, "nodeID");
        return nodeId + " IN (" + CategorySubquery.forNames(e.getCategoryNames()) + ")";
    }

    @Override
    public String visitCatincAndIn(CatincAndInExpr e) throws FilterParseException {
        String nodeId = schemaConfig.addColumn(tables, "nodeID");
        return nodeId + " IN (" + CategorySubquery.forGroups(e.getCategoryGroups()) + ")";
    }

    @Override
    public String visitCatincInExcept(CatincInExceptExpr e) throws FilterParseException {
        String nodeId = schemaConfig.addColumn(tables, "nodeID");
        String positive = CategorySubquery.forGroups(e.getPositiveGroups());
        String negative = CategorySubquery.forNames(e.getNegativeNames());
        return nodeId + " IN ((" + positive + ") EXCEPT (" + negative + "))";
    }

    @Override
    public String visitCatincNotIn(CatincNotInExpr e) throws FilterParseException {
        String nodeId = schemaConfig.addColumn(tables, "nodeID");
        return nodeId + " NOT IN (" + CategorySubquery.forNames(e.getCategoryNames()) + ")";
    }

    @Override
    public String visitIsService(IsServiceExpr e) throws FilterParseException {
        String col = schemaConfig.addColumn(tables, "serviceName");
        String name = e.getServiceName().replace("'", "''");
        return col + " = '" + name + "'";
    }

    @Override
    public String visitIsServiceIn(IsServiceInExpr e) throws FilterParseException {
        String col = schemaConfig.addColumn(tables, "serviceName");
        String inList = e.getServiceNames().stream()
                .map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        return col + " IN (" + inList + ")";
    }

    @Override
    public String visitNotIsService(NotIsServiceExpr e) throws FilterParseException {
        String ipAddrCol = schemaConfig.addColumn(tables, "ipAddr");
        String name = e.getServiceName().replace("'", "''");
        return ipAddrCol + " NOT IN (SELECT ifServices.ipAddr FROM ifServices, service WHERE service.serviceName ='"
                + name + "' AND service.serviceID = ifServices.serviceID)";
    }

    @Override
    public String visitColumnRef(ColumnRefExpr e) throws FilterParseException {
        String col = schemaConfig.addColumn(tables, e.getColumnName());
        if (e.getTypeCast() != null) {
            col = col + "::" + e.getTypeCast();
        }
        return col;
    }

    @Override
    public String visitPlaceholder(PlaceholderExpr e) {
        int idx = Integer.parseInt(e.getIndex());
        if (idx < 0 || idx >= extractedStrings.size()) {
            return "###@" + e.getIndex() + "@###";
        }
        return extractedStrings.get(idx);
    }

    @Override
    public String visitComparison(ComparisonExpr e) throws FilterParseException {
        String leftSql = e.getLeft().accept(this);
        String rightSql = e.getRight().accept(this);
        return leftSql + " " + e.getOp() + " " + rightSql;
    }

    @Override
    public String visitStringLiteral(StringLiteralExpr e) {
        return "'" + e.getValue().replace("'", "''") + "'";
    }

    @Override
    public String visitIplike(IplikeExpr e) throws FilterParseException {
        String leftSql = e.getLeft().accept(this);
        String rightSql;
        // Operator form "col IPLIKE pattern" may have unquoted pattern tokenized as identifier (ColumnRefExpr).
        if (e.getRight() instanceof ColumnRefExpr) {
            String pattern = ((ColumnRefExpr) e.getRight()).getColumnName();
            rightSql = "'" + pattern.replace("'", "''") + "'";
        } else {
            rightSql = e.getRight().accept(this);
        }
        return "IPLIKE(" + leftSql + ", " + rightSql + ")";
    }

    @Override
    public String visitIn(InExpr e) throws FilterParseException {
        String leftSql = e.getLeft().accept(this);
        List<String> parts = new ArrayList<>();
        for (Expr v : e.getValues()) {
            parts.add(v.accept(this));
        }
        String inList = String.join(", ", parts);
        return leftSql + (e.isNotIn() ? " NOT IN (" : " IN (") + inList + ")";
    }

    @Override
    public String visitIsNull(IsNullExpr e) throws FilterParseException {
        String exprSql = e.getExpr().accept(this);
        return e.isNotNull() ? exprSql + " IS NOT NULL" : exprSql + " IS NULL";
    }
}
