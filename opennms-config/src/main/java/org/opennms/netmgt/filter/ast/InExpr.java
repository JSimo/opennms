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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL IN: left IN (value1, value2, ...).
 * Emits "leftSql IN (right1Sql, right2Sql, ...)".
 */
final class InExpr implements Expr {

    private final Expr left;
    private final List<Expr> values;
    private final boolean notIn;

    InExpr(Expr left, List<Expr> values) {
        this(left, values, false);
    }

    InExpr(Expr left, List<Expr> values, boolean notIn) {
        this.left = Objects.requireNonNull(left, "left");
        Objects.requireNonNull(values, "values");
        List<Expr> copy = new ArrayList<>(values);
        for (Expr e : copy) {
            Objects.requireNonNull(e, "value");
        }
        this.values = Collections.unmodifiableList(copy);
        this.notIn = notIn;
    }

    Expr getLeft() {
        return left;
    }

    List<Expr> getValues() {
        return values;
    }

    boolean isNotIn() {
        return notIn;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitIn(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InExpr)) return false;
        InExpr that = (InExpr) o;
        return Objects.equals(left, that.left) && Objects.equals(values, that.values)
                && notIn == that.notIn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, values, notIn);
    }
}
