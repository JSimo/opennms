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
 * Node is in all of the given category groups (optimized form of AND of CatincInExpr/CatincExpr).
 * Each group is OR (node in any of those categories). Emits a single EXISTS with multiple JOINs
 * to category_node/categories instead of multiple EXISTS.
 */
final class CatincAndInExpr implements Expr {

    private final List<List<String>> categoryGroups;

    /**
     * @param categoryGroups each inner list is one IN group (OR); all groups are ANDed
     */
    public CatincAndInExpr(List<List<String>> categoryGroups) {
        Objects.requireNonNull(categoryGroups, "categoryGroups");
        List<List<String>> copy = new ArrayList<>();
        for (List<String> group : categoryGroups) {
            Objects.requireNonNull(group, "group");
            copy.add(Collections.unmodifiableList(new ArrayList<>(group)));
        }
        this.categoryGroups = Collections.unmodifiableList(copy);
    }

    public List<List<String>> getCategoryGroups() {
        return categoryGroups;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitCatincAndIn(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatincAndInExpr)) return false;
        CatincAndInExpr that = (CatincAndInExpr) o;
        return Objects.equals(categoryGroups, that.categoryGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryGroups);
    }
}
