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
 * Node is not in any of the given categories (normalized form of NOT CatincExpr / NOT CatincInExpr).
 * Emits {@code nodeID NOT IN (SELECT ...)} for hash anti-join.
 */
final class CatincNotInExpr implements Expr {

    private final List<String> categoryNames;

    public CatincNotInExpr(List<String> categoryNames) {
        Objects.requireNonNull(categoryNames, "categoryNames");
        List<String> copy = new ArrayList<>(categoryNames);
        for (String s : copy) {
            Objects.requireNonNull(s, "category name");
        }
        this.categoryNames = Collections.unmodifiableList(copy);
    }

    public List<String> getCategoryNames() {
        return categoryNames;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitCatincNotIn(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatincNotInExpr)) return false;
        CatincNotInExpr that = (CatincNotInExpr) o;
        return Objects.equals(categoryNames, that.categoryNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryNames);
    }
}
