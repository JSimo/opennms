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

/** OR of CatincExpr/CatincInExpr only → CatincInExpr (or single CatincExpr). */
final class MergeOrCatincToCatincInOptimizer extends BaseOptimizerStep {

    @Override
    protected Expr rewrite(Expr e) {
        if (e instanceof OrExpr) {
            List<Expr> rewritten = ((OrExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            List<String> catincNames = new ArrayList<>();
            for (Expr c : rewritten) {
                if (c instanceof CatincExpr) {
                    catincNames.add(((CatincExpr) c).getCategoryName());
                } else if (c instanceof CatincInExpr) {
                    catincNames.addAll(((CatincInExpr) c).getCategoryNames());
                } else {
                    catincNames = null;
                    break;
                }
            }
            if (catincNames != null && catincNames.size() >= 2) {
                return new CatincInExpr(catincNames);
            }
            if (catincNames != null && catincNames.size() == 1) {
                return new CatincExpr(catincNames.get(0));
            }
            return rewritten.size() == 1 ? rewritten.get(0) : new OrExpr(rewritten);
        }
        return recurse(e);
    }
}
