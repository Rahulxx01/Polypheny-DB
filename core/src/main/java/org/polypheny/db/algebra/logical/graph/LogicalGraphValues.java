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

package org.polypheny.db.algebra.logical.graph;

import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import org.polypheny.db.algebra.AbstractAlgNode;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.GraphAlg;
import org.polypheny.db.algebra.logical.LogicalValues;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.algebra.type.AlgDataTypeFieldImpl;
import org.polypheny.db.algebra.type.AlgDataTypeSystem;
import org.polypheny.db.algebra.type.AlgRecordType;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgOptTable;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.runtime.PolyCollections.PolyList;
import org.polypheny.db.schema.ModelTrait;
import org.polypheny.db.schema.graph.PolyEdge;
import org.polypheny.db.schema.graph.PolyEdge.RelationshipDirection;
import org.polypheny.db.schema.graph.PolyNode;
import org.polypheny.db.type.BasicPolyType;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Collation;
import org.polypheny.db.util.NlsString;
import org.polypheny.db.util.Pair;

@Getter
public class LogicalGraphValues extends AbstractAlgNode implements GraphAlg, RelationalTransformable {

    public static final BasicPolyType ID_TYPE = new BasicPolyType( AlgDataTypeSystem.DEFAULT, PolyType.VARCHAR, 36 );
    public static final BasicPolyType NODE_TYPE = new BasicPolyType( AlgDataTypeSystem.DEFAULT, PolyType.NODE );
    public static final BasicPolyType EDGE_TYPE = new BasicPolyType( AlgDataTypeSystem.DEFAULT, PolyType.EDGE );
    private final ImmutableList<PolyNode> nodes;
    private final ImmutableList<PolyEdge> edges;
    private final ImmutableList<ImmutableList<RexLiteral>> values;


    /**
     * Creates an <code>AbstractRelNode</code>.
     *
     * @param cluster
     * @param traitSet
     */
    public LogicalGraphValues( AlgOptCluster cluster, AlgTraitSet traitSet, List<PolyNode> nodes, List<PolyEdge> edges, ImmutableList<ImmutableList<RexLiteral>> values, AlgDataType rowType ) {
        super( cluster, traitSet );
        this.nodes = ImmutableList.copyOf( nodes );
        this.edges = ImmutableList.copyOf( edges );
        this.values = values;

        assert edges.stream().noneMatch( e -> e.direction == RelationshipDirection.NONE ) : "Edges which are created need to have a direction.";

        this.rowType = rowType;
    }


    public static LogicalGraphValues create(
            AlgOptCluster cluster,
            AlgTraitSet traitSet,
            AlgDataType rowType,
            ImmutableList<ImmutableList<RexLiteral>> values ) {
        return new LogicalGraphValues( cluster, traitSet, List.of(), List.of(), values, rowType );
    }


    public static LogicalGraphValues create(
            AlgOptCluster cluster,
            AlgTraitSet traitSet,
            List<Pair<String, PolyNode>> nodes,
            AlgDataType nodeType,
            List<Pair<String, PolyEdge>> edges,
            AlgDataType edgeType ) {

        List<AlgDataTypeField> fields = new ArrayList<>();

        int i = 0;
        for ( String name : Pair.left( nodes ).stream().filter( Objects::nonNull ).collect( Collectors.toList() ) ) {
            fields.add( new AlgDataTypeFieldImpl( name, i, nodeType ) );
            i++;
        }

        for ( String name : Pair.left( edges ).stream().filter( Objects::nonNull ).collect( Collectors.toList() ) ) {
            fields.add( new AlgDataTypeFieldImpl( name, i, edgeType ) );
            i++;
        }

        AlgRecordType rowType = new AlgRecordType( fields );

        return new LogicalGraphValues( cluster, traitSet, Pair.right( nodes ), Pair.right( edges ), ImmutableList.of(), rowType );

    }


    @Override
    public String algCompareString() {
        return "$" + getClass().getSimpleName() + "$" + nodes.hashCode() + "$" + edges.hashCode();
    }


    @Override
    public List<AlgNode> getRelationalEquivalent( List<AlgNode> values, List<AlgOptTable> entities ) {
        AlgTraitSet out = traitSet.replace( ModelTrait.RELATIONAL );
        AlgDataTypeFactory typeFactory = getCluster().getTypeFactory();

        AlgOptCluster cluster = AlgOptCluster.create( getCluster().getPlanner(), getCluster().getRexBuilder() );

        LogicalValues nodeValues = new LogicalValues( cluster, out, entities.get( 0 ).getRowType(), getNodeValues( nodes, typeFactory ) );
        if ( edges.isEmpty() ) {
            return List.of( nodeValues );
        }
        assert entities.size() == 2 && entities.get( 1 ) != null;
        LogicalValues edgeValues = new LogicalValues( cluster, out, entities.get( 1 ).getRowType(), getEdgeValues( edges, typeFactory ) );
        return Arrays.asList( nodeValues, edgeValues );
    }


    private ImmutableList<ImmutableList<RexLiteral>> getNodeValues( ImmutableList<PolyNode> nodes, AlgDataTypeFactory typeFactory ) {
        ImmutableList.Builder<ImmutableList<RexLiteral>> rows = ImmutableList.builder();
        for ( PolyNode node : nodes ) {
            ImmutableList.Builder<RexLiteral> row = ImmutableList.builder();
            row.add( new RexLiteral( new NlsString( node.id, StandardCharsets.ISO_8859_1.name(), Collation.IMPLICIT ), ID_TYPE, PolyType.CHAR ) );
            row.add( new RexLiteral( node, NODE_TYPE, PolyType.NODE ) );

            PolyList<RexLiteral> labels = node.getRexLabels();
            AlgDataType arrayType = typeFactory.createArrayType( typeFactory.createPolyType( PolyType.VARCHAR, 255 ), labels.size(), 1 );
            row.add( new RexLiteral( labels, arrayType, PolyType.ARRAY ) );
            rows.add( row.build() );
        }

        return rows.build();
    }


    private ImmutableList<ImmutableList<RexLiteral>> getEdgeValues( ImmutableList<PolyEdge> edges, AlgDataTypeFactory typeFactory ) {
        ImmutableList.Builder<ImmutableList<RexLiteral>> rows = ImmutableList.builder();
        for ( PolyEdge edge : edges ) {
            ImmutableList.Builder<RexLiteral> row = ImmutableList.builder();
            row.add( new RexLiteral( new NlsString( edge.id, StandardCharsets.ISO_8859_1.name(), Collation.IMPLICIT ), ID_TYPE, PolyType.CHAR ) );
            row.add( new RexLiteral( edge, EDGE_TYPE, PolyType.EDGE ) );

            PolyList<RexLiteral> labels = edge.getRexLabels();
            AlgDataType arrayType = typeFactory.createArrayType( typeFactory.createPolyType( PolyType.VARCHAR, 255 ), labels.size(), 1 );

            row.add( new RexLiteral( edge.getRexLabels(), arrayType, PolyType.ARRAY ) );
            row.add( new RexLiteral( new NlsString( edge.source, StandardCharsets.ISO_8859_1.name(), Collation.IMPLICIT ), ID_TYPE, PolyType.CHAR ) );
            row.add( new RexLiteral( new NlsString( edge.target, StandardCharsets.ISO_8859_1.name(), Collation.IMPLICIT ), ID_TYPE, PolyType.CHAR ) );
            rows.add( row.build() );
        }

        return rows.build();
    }


    @Override
    public NodeType getNodeType() {
        return NodeType.VALUES;
    }


    public static LogicalGraphValues merge( List<LogicalGraphValues> values ) {
        return new LogicalGraphValues(
                values.get( 0 ).getCluster(),
                values.get( 0 ).getTraitSet(),
                values.stream().flatMap( v -> v.nodes.stream() ).collect( Collectors.toList() ),
                values.stream().flatMap( v -> v.edges.stream() ).collect( Collectors.toList() ),
                ImmutableList.copyOf( values.stream().flatMap( v -> v.values.stream() ).collect( Collectors.toList() ) ),
                new AlgRecordType( values.stream().flatMap( v -> v.rowType.getFieldList().stream() ).collect( Collectors.toList() ) ) );
    }


    public boolean isEmptyGraphValues() {
        return edges.isEmpty() && nodes.isEmpty();
    }

}
