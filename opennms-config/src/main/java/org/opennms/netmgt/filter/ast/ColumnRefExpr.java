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

import java.util.Objects;

/**
 * Reference to a schema column (e.g. nodeLabel, ipAddr).
 * Emission uses DatabaseSchemaConfig.addColumn(tables, columnName) to resolve
 * to table.column form.
 */
final class ColumnRefExpr implements Expr {

    private final String columnName;
    /** Optional PostgreSQL cast suffix: TIMESTAMP or INET */
    private final String typeCast;

    ColumnRefExpr(String columnName) {
        this(columnName, null);
    }

    ColumnRefExpr(String columnName, String typeCast) {
        this.columnName = Objects.requireNonNull(columnName, "columnName");
        this.typeCast = typeCast;
    }

    String getColumnName() {
        return columnName;
    }

    String getTypeCast() {
        return typeCast;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitColumnRef(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnRefExpr)) return false;
        ColumnRefExpr that = (ColumnRefExpr) o;
        return Objects.equals(columnName, that.columnName)
                && Objects.equals(typeCast, that.typeCast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, typeCast);
    }
}
