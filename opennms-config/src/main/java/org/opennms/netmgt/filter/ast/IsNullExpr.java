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
 * SQL IS NULL / IS NOT NULL: expr IS NULL or expr IS NOT NULL.
 * Emits "exprSql IS NULL" or "exprSql IS NOT NULL".
 */
final class IsNullExpr implements Expr {

    private final Expr expr;
    private final boolean notNull;

    /**
     * @param expr    the expression (e.g. column reference)
     * @param notNull if true emit "IS NOT NULL", if false emit "IS NULL"
     */
    public IsNullExpr(Expr expr, boolean notNull) {
        this.expr = Objects.requireNonNull(expr, "expr");
        this.notNull = notNull;
    }

    public Expr getExpr() {
        return expr;
    }

    /** True for IS NOT NULL, false for IS NULL. */
    public boolean isNotNull() {
        return notNull;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitIsNull(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IsNullExpr)) return false;
        IsNullExpr that = (IsNullExpr) o;
        return notNull == that.notNull && Objects.equals(expr, that.expr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expr, notNull);
    }
}
