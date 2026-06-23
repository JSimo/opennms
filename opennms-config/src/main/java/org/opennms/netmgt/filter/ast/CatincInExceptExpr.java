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
 * Node is in the positive category set and not in the negative set (optimized form of
 * AND of CatincIn/Catinc/CatincAndIn with Not(Catinc)/Not(CatincIn)). Emits a single
 * {@code nodeID IN ((positive) EXCEPT (negative))} so the planner materializes the
 * filtered node set once.
 */
final class CatincInExceptExpr implements Expr {

    private final List<List<String>> positiveGroups;
    private final List<String> negativeNames;

    /**
     * @param positiveGroups same as CatincAndInExpr: node must be in at least one category from each group
     * @param negativeNames  node must not be in any of these categories
     */
    public CatincInExceptExpr(List<List<String>> positiveGroups, List<String> negativeNames) {
        Objects.requireNonNull(positiveGroups, "positiveGroups");
        Objects.requireNonNull(negativeNames, "negativeNames");
        List<List<String>> copyPos = new ArrayList<>();
        for (List<String> group : positiveGroups) {
            Objects.requireNonNull(group, "group");
            copyPos.add(Collections.unmodifiableList(new ArrayList<>(group)));
        }
        this.positiveGroups = Collections.unmodifiableList(copyPos);
        List<String> copyNeg = new ArrayList<>(negativeNames);
        for (String s : copyNeg) {
            Objects.requireNonNull(s, "negative category name");
        }
        this.negativeNames = Collections.unmodifiableList(copyNeg);
    }

    public List<List<String>> getPositiveGroups() {
        return positiveGroups;
    }

    public List<String> getNegativeNames() {
        return negativeNames;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitCatincInExcept(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatincInExceptExpr)) return false;
        CatincInExceptExpr that = (CatincInExceptExpr) o;
        return Objects.equals(positiveGroups, that.positiveGroups)
                && Objects.equals(negativeNames, that.negativeNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positiveGroups, negativeNames);
    }
}
