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

/** AND of (positive category) with Not(negative category) → CatincInExceptExpr + rest. */
final class MergeAndCategoryExceptOptimizer extends BaseOptimizerStep {

    @Override
    protected Expr rewrite(Expr e) {
        if (e instanceof AndExpr) {
            List<Expr> rewritten = ((AndExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            List<List<String>> positiveGroups = new ArrayList<>();
            List<String> negativeNames = new ArrayList<>();
            List<Expr> rest = new ArrayList<>();

            for (Expr term : rewritten) {
                if (term instanceof CatincExpr) {
                    positiveGroups.add(List.of(((CatincExpr) term).getCategoryName()));
                } else if (term instanceof CatincInExpr) {
                    positiveGroups.add(new ArrayList<>(((CatincInExpr) term).getCategoryNames()));
                } else if (term instanceof CatincAndInExpr) {
                    positiveGroups.addAll(((CatincAndInExpr) term).getCategoryGroups());
                } else if (term instanceof CatincNotInExpr) {
                    negativeNames.addAll(((CatincNotInExpr) term).getCategoryNames());
                } else {
                    rest.add(term);
                }
            }

            if (!positiveGroups.isEmpty() && !negativeNames.isEmpty()) {
                List<Expr> newChildren = new ArrayList<>();
                newChildren.add(new CatincInExceptExpr(positiveGroups, negativeNames));
                newChildren.addAll(rest);
                return newChildren.size() == 1 ? newChildren.get(0) : new AndExpr(newChildren);
            }
            return new AndExpr(rewritten);
        }
        return recurse(e);
    }
}
