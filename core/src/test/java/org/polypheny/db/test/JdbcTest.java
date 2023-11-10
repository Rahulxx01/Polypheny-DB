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

package org.polypheny.db.test;


import java.util.List;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.relational.RelModify;
import org.polypheny.db.algebra.logical.relational.LogicalRelModify;
import org.polypheny.db.catalog.entity.LogicalEntity;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.impl.AbstractEntity;
import org.polypheny.db.schema.types.ModifiableTable;


/**
 * Tests for using Polypheny-DB via JDBC.
 */
public class JdbcTest {

    /**
     * Abstract base class for implementations of {@link ModifiableTable}.
     */
    public abstract static class AbstractModifiableTable extends AbstractEntity implements ModifiableTable {

        protected AbstractModifiableTable( String tableName ) {
            super( null, null, null );
        }


        //@Override
        public RelModify<?> toModificationAlg(
                AlgOptCluster cluster,
                LogicalEntity entity,
                AlgNode child,
                RelModify.Operation operation,
                List<String> updateColumnList,
                List<RexNode> sourceExpressionList,
                boolean flattened ) {
            return LogicalRelModify.create(
                    entity,
                    child,
                    operation,
                    updateColumnList,
                    sourceExpressionList,
                    flattened );
        }

    }

}
