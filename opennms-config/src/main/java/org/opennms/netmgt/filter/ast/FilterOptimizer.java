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

import java.util.List;

/**
 * Applies a sequence of optimizer steps to the filter AST to produce faster SQL.
 * <p>
 * Order of steps (OR merges before AND; NOT category normalized so MergeAndCategoryExcept sees CatincNotInExpr):
 * <ol>
 *   <li>Flatten nested AND/OR</li>
 *   <li>Deduplicate AND terms</li>
 *   <li>NOT(Catinc) / NOT(CatincIn) → CatincNotInExpr</li>
 *   <li>OR of CatincExpr/CatincInExpr only → CatincInExpr</li>
 *   <li>OR of IsServiceExpr only → IsServiceInExpr</li>
 *   <li>AND of CatincInExpr/CatincExpr only → CatincAndInExpr</li>
 *   <li>AND of (positive category) with CatincNotInExpr → CatincInExceptExpr</li>
 * </ol>
 */
final class FilterOptimizer {

    private static final List<OptimizerStep> STEPS = List.of(
            new FlattenAndOrOptimizer(),
            new DeduplicateAndOptimizer(),
            new NormalizeNotCategoryOptimizer(),
            new MergeOrCatincToCatincInOptimizer(),
            new MergeOrIsServiceToIsServiceInOptimizer(),
            new MergeAndCategoryToCatincAndInOptimizer(),
            new MergeAndCategoryExceptOptimizer()
    );

    /**
     * Optimize the tree rooted at {@code root} by applying each step in turn.
     *
     * @param root root expression (may be null)
     * @return optimized tree, or null if root was null
     */
    public Expr optimize(Expr root) {
        if (root == null) {
            return null;
        }
        Expr result = root;
        for (OptimizerStep step : STEPS) {
            result = step.optimize(result);
        }
        return result;
    }
}
