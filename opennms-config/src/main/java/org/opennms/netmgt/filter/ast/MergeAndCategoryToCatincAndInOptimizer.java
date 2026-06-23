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

/** AND of CatincExpr/CatincInExpr only → CatincAndInExpr (or single CatincExpr/CatincInExpr). */
final class MergeAndCategoryToCatincAndInOptimizer extends BaseOptimizerStep {

    @Override
    protected Expr rewrite(Expr e) {
        if (e instanceof AndExpr) {
            List<Expr> rewritten = ((AndExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            List<List<String>> categoryGroups = new ArrayList<>();
            for (Expr term : rewritten) {
                if (term instanceof CatincExpr) {
                    categoryGroups.add(List.of(((CatincExpr) term).getCategoryName()));
                } else if (term instanceof CatincInExpr) {
                    categoryGroups.add(new ArrayList<>(((CatincInExpr) term).getCategoryNames()));
                } else {
                    categoryGroups = null;
                    break;
                }
            }
            if (categoryGroups != null && categoryGroups.size() >= 2) {
                return new CatincAndInExpr(categoryGroups);
            }
            if (categoryGroups != null && categoryGroups.size() == 1) {
                List<String> names = categoryGroups.get(0);
                return names.size() == 1 ? new CatincExpr(names.get(0)) : new CatincInExpr(names);
            }
            return new AndExpr(rewritten);
        }
        return recurse(e);
    }
}
