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

package org.polypheny.db.algebra.logical.document;

import java.util.List;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgShuttle;
import org.polypheny.db.algebra.core.document.DocumentValues;
import org.polypheny.db.algebra.core.relational.RelationalTransformable;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.document.PolyDocument;


public class LogicalDocumentValues extends DocumentValues implements RelationalTransformable {


    /**
     * Java representation of multiple documents, which can be retrieved in the original BSON format form
     * or in the substantiated relational form, where the documents are bundled into a BSON string
     *
     * BSON format
     * <pre><code>
     *     "_id": ObjectId(23kdf232123)
     *     "key": "value",
     *     "key1": "value"
     * </pre></code>
     *
     * becomes
     *
     * Column format
     * <pre><code>
     *     "_id": ObjectId(23kdf232123)
     *     "_data": {
     *         "key": "value",
     *         "key1": "value"
     *     }
     *
     * </pre></code>
     *
     * @param cluster the cluster, which holds the information regarding the ongoing operation
     * @param traitSet the used traitSet
     * @param document the documents in their native BSON format
     */
    public LogicalDocumentValues( AlgOptCluster cluster, AlgTraitSet traitSet, List<PolyDocument> document ) {
        super( cluster, traitSet, document );
    }


    public static AlgNode create( AlgOptCluster cluster, List<PolyDocument> documents ) {
        final AlgTraitSet traitSet = cluster.traitSetOf( Convention.NONE );
        return new LogicalDocumentValues( cluster, traitSet, documents );
    }


    public static LogicalDocumentValues createOneTuple( AlgOptCluster cluster ) {
        final AlgDataType rowType =
                cluster.getTypeFactory()
                        .builder()
                        .add( "ZERO", null, PolyType.INTEGER )
                        .nullable( false )
                        .build();
        return new LogicalDocumentValues( cluster, cluster.traitSet(), List.of( new PolyDocument() ) );
    }


    @Override
    public NamespaceType getModel() {
        return NamespaceType.DOCUMENT;
    }


    @Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        assert traitSet.containsIfApplicable( Convention.NONE );
        assert inputs.isEmpty();
        return new LogicalDocumentValues( getCluster(), traitSet, documents );
    }


    @Override
    public AlgNode accept( AlgShuttle shuttle ) {
        return shuttle.visit( this );
    }

}
