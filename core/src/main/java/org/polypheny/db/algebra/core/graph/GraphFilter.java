/*
 * Copyright 2019-2022 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.algebra.core.graph;

import lombok.Getter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.GraphAlg;
import org.polypheny.db.algebra.SingleAlg;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexNode;

public abstract class GraphFilter extends SingleAlg implements GraphAlg {

    @Getter
    private final RexNode condition;


    /**
     * Creates a <code>SingleRel</code>.
     *
     * @param cluster Cluster this relational expression belongs to
     * @param traits
     * @param input Input relational expression
     * @param condition
     */
    protected GraphFilter( AlgOptCluster cluster, AlgTraitSet traits, AlgNode input, RexNode condition ) {
        super( cluster, traits, input );
        this.condition = condition;
    }


    @Override
    public String algCompareString() {
        return "$" + getClass().getSimpleName() + "$" + this.condition.hashCode() + "$" + getInput().algCompareString();
    }


    @Override
    public NodeType getNodeType() {
        return NodeType.FILTER;
    }

}
