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

package org.polypheny.db.processing;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.linq4j.Enumerable;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.AlgStructuredTypeFlattener;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.core.common.Modify;
import org.polypheny.db.algebra.core.common.Modify.Operation;
import org.polypheny.db.algebra.logical.document.LogicalDocumentModify;
import org.polypheny.db.algebra.logical.document.LogicalDocumentScan;
import org.polypheny.db.algebra.logical.document.LogicalDocumentValues;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgModify;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgScan;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgValues;
import org.polypheny.db.algebra.logical.relational.LogicalRelModify;
import org.polypheny.db.algebra.logical.relational.LogicalValues;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.algebra.type.AlgDataTypeFieldImpl;
import org.polypheny.db.algebra.type.AlgDataTypeSystem;
import org.polypheny.db.algebra.type.AlgRecordType;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.allocation.AllocationCollection;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.allocation.AllocationGraph;
import org.polypheny.db.catalog.entity.allocation.AllocationPlacement;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.logical.LogicalCollection;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalGraph;
import org.polypheny.db.catalog.entity.logical.LogicalPrimaryKey;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.catalog.snapshot.AllocSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalRelSnapshot;
import org.polypheny.db.catalog.snapshot.Snapshot;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.partition.PartitionManager;
import org.polypheny.db.partition.PartitionManagerFactory;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.routing.RoutingManager;
import org.polypheny.db.schema.trait.ModelTrait;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeFactoryImpl;
import org.polypheny.db.type.entity.PolyInteger;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.document.PolyDocument;
import org.polypheny.db.type.entity.graph.PolyGraph;
import org.polypheny.db.util.LimitIterator;


@Slf4j
public class DataMigratorImpl implements DataMigrator {

    @Override
    public void copyGraphData( AllocationGraph to, LogicalGraph from, Transaction transaction ) {
        Statement statement = transaction.createStatement();

        AlgBuilder builder = AlgBuilder.create( statement );

        LogicalLpgScan scan = (LogicalLpgScan) builder.lpgScan( from ).build();

        AlgNode routed = RoutingManager.getInstance().getFallbackRouter().handleGraphScan( scan, statement, null, List.of( to.id ) );

        AlgRoot algRoot = AlgRoot.of( routed, Kind.SELECT );

        AlgStructuredTypeFlattener typeFlattener = new AlgStructuredTypeFlattener(
                AlgBuilder.create( statement, algRoot.alg.getCluster() ),
                algRoot.alg.getCluster().getRexBuilder(),
                algRoot.alg::getCluster,
                true );
        algRoot = algRoot.withAlg( typeFlattener.rewrite( algRoot.alg ) );

        PolyImplementation<Object> result = statement.getQueryProcessor().prepareQuery(
                algRoot,
                algRoot.alg.getCluster().getTypeFactory().builder().build(),
                true,
                false,
                false );

        final Enumerable<Object> enumerable = result.enumerable( statement.getDataContext() );

        Iterator<Object> sourceIterator = enumerable.iterator();

        if ( sourceIterator.hasNext() ) {
            PolyGraph graph = (PolyGraph) sourceIterator.next();

            if ( graph.getEdges().isEmpty() && graph.getNodes().isEmpty() ) {
                // nothing to copy
                return;
            }

            // we have a new statement
            statement = transaction.createStatement();
            builder = AlgBuilder.create( statement );

            LogicalLpgValues values = getLogicalLpgValues( builder, graph );

            LogicalLpgModify modify = new LogicalLpgModify( builder.getCluster(), builder.getCluster().traitSetOf( ModelTrait.GRAPH ), to, values, Modify.Operation.INSERT, null, null );

            AlgNode routedModify = RoutingManager.getInstance().getDmlRouter().routeGraphDml( modify, statement, from, List.of( to.id ) );

            result = statement.getQueryProcessor().prepareQuery(
                    AlgRoot.of( routedModify, Kind.SELECT ),
                    routedModify.getCluster().getTypeFactory().builder().build(),
                    true,
                    false,
                    false );

            final Enumerable<Object> modifyEnumerable = result.enumerable( statement.getDataContext() );

            Iterator<Object> modifyIterator = modifyEnumerable.iterator();
            if ( modifyIterator.hasNext() ) {
                modifyIterator.next();
            }
        }
    }


    @Override
    public void copyDocData( AllocationCollection to, LogicalCollection from, Transaction transaction ) {
        Statement statement = transaction.createStatement();

        AlgBuilder builder = AlgBuilder.create( statement );

        LogicalDocumentScan scan = (LogicalDocumentScan) builder.documentScan( from ).build();

        AlgNode routed = RoutingManager.getInstance().getFallbackRouter().handleDocScan( scan, statement, List.of( to.id ) );

        AlgRoot algRoot = AlgRoot.of( routed, Kind.SELECT );

        AlgStructuredTypeFlattener typeFlattener = new AlgStructuredTypeFlattener(
                AlgBuilder.create( statement, algRoot.alg.getCluster() ),
                algRoot.alg.getCluster().getRexBuilder(),
                algRoot.alg::getCluster,
                true );
        algRoot = algRoot.withAlg( typeFlattener.rewrite( algRoot.alg ) );

        PolyImplementation<PolyValue> result = statement.getQueryProcessor().prepareQuery(
                algRoot,
                algRoot.alg.getCluster().getTypeFactory().builder().build(),
                true,
                false,
                false );

        final Enumerable<PolyValue> enumerable = result.enumerable( statement.getDataContext() );

        Iterator<PolyValue> sourceIterator = enumerable.iterator();

        if ( sourceIterator.hasNext() ) {
            // we have a new statement
            statement = transaction.createStatement();
            builder = AlgBuilder.create( statement );

            LogicalDocumentValues values = getLogicalDocValues( builder, sourceIterator );

            LogicalDocumentModify modify = new LogicalDocumentModify( builder.getCluster().traitSetOf( ModelTrait.DOCUMENT ), to, values, Modify.Operation.INSERT, null, null, null );

            AlgNode routedModify = RoutingManager.getInstance().getDmlRouter().routeDocumentDml( modify, statement, to.adapterId );

            result = statement.getQueryProcessor().prepareQuery(
                    AlgRoot.of( routedModify, Kind.SELECT ),
                    routedModify.getCluster().getTypeFactory().builder().build(),
                    true,
                    false,
                    false );

            final Enumerable<?> modifyEnumerable = result.enumerable( statement.getDataContext() );

            Iterator<?> modifyIterator = modifyEnumerable.iterator();
            if ( modifyIterator.hasNext() ) {
                modifyIterator.next();
            }
        }
    }


    private LogicalDocumentValues getLogicalDocValues( AlgBuilder builder, Iterator<PolyValue> iterator ) {
        List<PolyDocument> documents = new ArrayList<>();

        iterator.forEachRemaining( e -> documents.add( e.asDocument() ) );

        return new LogicalDocumentValues( builder.getCluster(), builder.getCluster().traitSet(), documents );
    }


    @NotNull
    private static LogicalLpgValues getLogicalLpgValues( AlgBuilder builder, PolyGraph graph ) {
        List<AlgDataTypeField> fields = new ArrayList<>();
        int index = 0;
        if ( !graph.getNodes().isEmpty() ) {
            fields.add( new AlgDataTypeFieldImpl( "n", index, builder.getTypeFactory().createPolyType( PolyType.NODE ) ) );
            index++;
        }
        if ( !graph.getEdges().isEmpty() ) {
            fields.add( new AlgDataTypeFieldImpl( "e", index, builder.getTypeFactory().createPolyType( PolyType.EDGE ) ) );
        }

        return new LogicalLpgValues( builder.getCluster(), builder.getCluster().traitSetOf( ModelTrait.GRAPH ), graph.getNodes().values(), graph.getEdges().values(), ImmutableList.of(), new AlgRecordType( fields ) );
    }


    @Override
    public void copyData(
            Transaction transaction,
            CatalogAdapter store,
            LogicalTable source,
            List<LogicalColumn> columns,
            AllocationPlacement target ) {
        Snapshot snapshot = Catalog.snapshot();
        LogicalRelSnapshot relSnapshot = snapshot.rel();
        LogicalPrimaryKey primaryKey = relSnapshot.getPrimaryKey( source.primaryKey ).orElseThrow();

        // Check Lists
        /*List<AllocationColumn> targetColumnPlacements = new LinkedList<>();
        for ( LogicalColumn logicalColumn : columns ) {
            targetColumnPlacements.add( Catalog.getInstance().getSnapshot().alloc().getColumn( store.id, logicalColumn.id ).orElseThrow() );
        }*/

        List<LogicalColumn> selectedColumns = new ArrayList<>( columns );

        // Add primary keys to select column list
        for ( long cid : primaryKey.columnIds ) {
            LogicalColumn logicalColumn = relSnapshot.getColumn( cid ).orElseThrow();
            if ( !selectedColumns.contains( logicalColumn ) ) {
                selectedColumns.add( logicalColumn );
            }
        }

        // We need a columnPlacement for every partition
        Map<Long, List<AllocationColumn>> placementDistribution = new HashMap<>();
        PartitionProperty property = snapshot.alloc().getPartitionProperty( source.id ).orElseThrow();

        List<AllocationEntity> allocs = snapshot.alloc().getAllocsOfPlacement( target.id );
        if ( allocs.size() > 1 ) {
            // is partitioned
            PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
            PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( property.partitionType );
            placementDistribution = partitionManager.getRelevantPlacements( source, allocs, Collections.singletonList( store.id ) );
        } else {
            placementDistribution.put( allocs.get( 0 ).partitionId, selectSourcePlacements( source, selectedColumns, target.adapterId ) );
        }

        for ( Long id : property.partitionIds ) {
            Statement sourceStatement = transaction.createStatement();
            Statement targetStatement = transaction.createStatement();

            Map<Long, List<AllocationColumn>> subDistribution = new HashMap<>( placementDistribution );
            subDistribution.keySet().retainAll( List.of( id ) );
            AlgRoot sourceAlg = getSourceIterator( sourceStatement, source, subDistribution );
            AlgRoot targetAlg;
            AllocationTable allocation = snapshot.alloc().getEntity( store.id, source.id ).map( a -> a.unwrap( AllocationTable.class ) ).orElseThrow();
            if ( allocation.getColumns().size() == columns.size() ) {
                // There have been no placements for this table on this store before. Build insert statement
                targetAlg = buildInsertStatement( targetStatement, allocation.getColumns(), allocation );
            } else {
                // Build update statement
                targetAlg = buildUpdateStatement( targetStatement, allocation.getColumns(), allocation );
            }

            // Execute Query
            executeQuery( allocation.getColumns(), sourceAlg, sourceStatement, targetStatement, targetAlg, false, false );
        }
    }


    @Override
    public void executeQuery( List<AllocationColumn> selectedColumns, AlgRoot sourceAlg, Statement sourceStatement, Statement targetStatement, AlgRoot targetAlg, boolean isMaterializedView, boolean doesSubstituteOrderBy ) {
        try {
            PolyImplementation<Object> result;
            if ( isMaterializedView ) {
                result = sourceStatement.getQueryProcessor().prepareQuery(
                        sourceAlg,
                        sourceAlg.alg.getCluster().getTypeFactory().builder().build(),
                        false,
                        false,
                        doesSubstituteOrderBy );
            } else {
                result = sourceStatement.getQueryProcessor().prepareQuery(
                        sourceAlg,
                        sourceAlg.alg.getCluster().getTypeFactory().builder().build(),
                        true,
                        false,
                        false );
            }
            final Enumerable<Object> enumerable = result.enumerable( sourceStatement.getDataContext() );
            Iterator<Object> sourceIterator = enumerable.iterator();

            Map<Long, Integer> resultColMapping = new HashMap<>();
            for ( AllocationColumn column : selectedColumns ) {
                int i = 0;
                for ( AlgDataTypeField metaData : result.getRowType().getFieldList() ) {
                    if ( metaData.getName().equalsIgnoreCase( column.getLogicalColumnName() ) ) {
                        resultColMapping.put( column.getColumnId(), i );
                    }
                    i++;
                }
            }
            if ( isMaterializedView ) {
                for ( AllocationColumn column : selectedColumns ) {
                    if ( !resultColMapping.containsKey( column.getColumnId() ) ) {
                        int i = resultColMapping.values().stream().mapToInt( v -> v ).max().orElseThrow( NoSuchElementException::new );
                        resultColMapping.put( column.getColumnId(), i + 1 );
                    }
                }
            }

            int batchSize = RuntimeConfig.DATA_MIGRATOR_BATCH_SIZE.getInteger();
            int i = 0;
            while ( sourceIterator.hasNext() ) {
                List<List<PolyValue>> rows = MetaImpl.collect( result.getCursorFactory(), LimitIterator.of( sourceIterator, batchSize ), new ArrayList<>() ).stream().map( o -> o.stream().map( e -> (PolyValue) e ).collect( Collectors.toList() ) ).collect( Collectors.toList() );
                Map<Long, List<PolyValue>> values = new HashMap<>();

                for ( List<PolyValue> list : rows ) {
                    for ( Map.Entry<Long, Integer> entry : resultColMapping.entrySet() ) {
                        if ( !values.containsKey( entry.getKey() ) ) {
                            values.put( entry.getKey(), new LinkedList<>() );
                        }
                        if ( isMaterializedView ) {
                            if ( entry.getValue() > list.size() - 1 ) {
                                values.get( entry.getKey() ).add( PolyInteger.of( i ) );
                                i++;
                            } else {
                                values.get( entry.getKey() ).add( list.get( entry.getValue() ) );
                            }
                        } else {
                            values.get( entry.getKey() ).add( list.get( entry.getValue() ) );
                        }
                    }
                }
                List<AlgDataTypeField> fields;
                if ( isMaterializedView ) {
                    fields = targetAlg.alg.getEntity().getRowType().getFieldList();
                } else {
                    fields = sourceAlg.validatedRowType.getFieldList();
                }

                for ( Map.Entry<Long, List<PolyValue>> v : values.entrySet() ) {
                    targetStatement.getDataContext().addParameterValues(
                            v.getKey(),
                            fields.get( resultColMapping.get( v.getKey() ) ).getType(),
                            v.getValue() );
                }

                Iterator<?> iterator = targetStatement.getQueryProcessor()
                        .prepareQuery( targetAlg, sourceAlg.validatedRowType, true, false, false )
                        .enumerable( targetStatement.getDataContext() )
                        .iterator();
                //noinspection WhileLoopReplaceableByForEach
                while ( iterator.hasNext() ) {
                    iterator.next();
                }
                targetStatement.getDataContext().resetParameterValues();
            }
        } catch ( Throwable t ) {
            throw new RuntimeException( t );
        }
    }


    @Override
    public AlgRoot buildDeleteStatement( Statement statement, List<AllocationColumn> to, AllocationEntity allocation ) {
        AlgOptCluster cluster = AlgOptCluster.create(
                statement.getQueryProcessor().getPlanner(),
                new RexBuilder( statement.getTransaction().getTypeFactory() ), null, statement.getTransaction().getSnapshot() );
        AlgDataTypeFactory typeFactory = new PolyTypeFactoryImpl( AlgDataTypeSystem.DEFAULT );

        List<String> columnNames = new LinkedList<>();
        List<RexNode> values = new LinkedList<>();
        for ( AllocationColumn ccp : to ) {
            LogicalColumn logicalColumn = Catalog.getInstance().getSnapshot().rel().getColumn( ccp.columnId ).orElseThrow();
            columnNames.add( ccp.getLogicalColumnName() );
            values.add( new RexDynamicParam( logicalColumn.getAlgDataType( typeFactory ), (int) logicalColumn.id ) );
        }
        AlgBuilder builder = AlgBuilder.create( statement, cluster );
        builder.push( LogicalValues.createOneRow( cluster ) );
        builder.project( values, columnNames );

        AlgNode node = LogicalRelModify.create( allocation, builder.build(), Modify.Operation.DELETE, null, null, false );

        return AlgRoot.of( node, Kind.DELETE );
    }


    @Override
    public AlgRoot buildInsertStatement( Statement statement, List<AllocationColumn> to, AllocationEntity allocation ) {

        AlgOptCluster cluster = AlgOptCluster.create(
                statement.getQueryProcessor().getPlanner(),
                new RexBuilder( statement.getTransaction().getTypeFactory() ), null, statement.getDataContext().getSnapshot() );
        AlgDataTypeFactory typeFactory = new PolyTypeFactoryImpl( AlgDataTypeSystem.DEFAULT );

        // while adapters should be able to handle unsorted columnIds for prepared indexes,
        // this often leads to errors, and can be prevented by sorting
        List<AllocationColumn> placements = to.stream().sorted( Comparator.comparingLong( p -> p.columnId ) ).collect( Collectors.toList() );

        List<String> columnNames = new LinkedList<>();
        List<RexNode> values = new LinkedList<>();
        for ( AllocationColumn ccp : placements ) {
            LogicalColumn logicalColumn = Catalog.getInstance().getSnapshot().rel().getColumn( ccp.columnId ).orElseThrow();
            columnNames.add( ccp.getLogicalColumnName() );
            values.add( new RexDynamicParam( logicalColumn.getAlgDataType( typeFactory ), (int) logicalColumn.id ) );
        }
        AlgBuilder builder = AlgBuilder.create( statement, cluster );
        builder.push( LogicalValues.createOneRow( cluster ) );
        builder.project( values, columnNames );

        AlgNode node = LogicalRelModify.create( allocation, builder.build(), Operation.INSERT, null, null, false );

        return AlgRoot.of( node, Kind.INSERT );
    }


    private AlgRoot buildUpdateStatement( Statement statement, List<AllocationColumn> to, AllocationEntity allocation ) {
        AlgOptCluster cluster = AlgOptCluster.create(
                statement.getQueryProcessor().getPlanner(),
                new RexBuilder( statement.getTransaction().getTypeFactory() ), null, statement.getDataContext().getSnapshot() );

        AlgDataTypeFactory typeFactory = new PolyTypeFactoryImpl( AlgDataTypeSystem.DEFAULT );

        AlgBuilder builder = AlgBuilder.create( statement, cluster );
        builder.scan( allocation );

        // build condition
        RexNode condition = null;
        LogicalRelSnapshot snapshot = Catalog.getInstance().getSnapshot().rel();
        LogicalTable catalogTable = snapshot.getTable( to.get( 0 ).logicalTableId ).orElseThrow();
        LogicalPrimaryKey primaryKey = snapshot.getPrimaryKey( catalogTable.primaryKey ).orElseThrow();
        for ( long cid : primaryKey.columnIds ) {
            AllocationColumn ccp = Catalog.getInstance().getSnapshot().alloc().getColumn( to.get( 0 ).placementId, cid ).orElseThrow();
            LogicalColumn logicalColumn = snapshot.getColumn( cid ).orElseThrow();
            RexNode c = builder.equals(
                    builder.field( ccp.getLogicalColumnName() ),
                    new RexDynamicParam( logicalColumn.getAlgDataType( typeFactory ), (int) logicalColumn.id )
            );
            if ( condition == null ) {
                condition = c;
            } else {
                condition = builder.and( condition, c );
            }
        }
        builder = builder.filter( condition );

        List<String> columnNames = new LinkedList<>();
        List<RexNode> values = new LinkedList<>();
        for ( AllocationColumn ccp : to ) {
            LogicalColumn logicalColumn = snapshot.getColumn( ccp.columnId ).orElseThrow();
            columnNames.add( ccp.getLogicalColumnName() );
            values.add( new RexDynamicParam( logicalColumn.getAlgDataType( typeFactory ), (int) logicalColumn.id ) );
        }

        builder.projectPlus( values );

        AlgNode node = builder.push(
                LogicalRelModify.create( allocation, builder.build(), Modify.Operation.UPDATE, columnNames, values, false )
        ).build();
        AlgRoot algRoot = AlgRoot.of( node, Kind.UPDATE );
        AlgStructuredTypeFlattener typeFlattener = new AlgStructuredTypeFlattener(
                AlgBuilder.create( statement, algRoot.alg.getCluster() ),
                algRoot.alg.getCluster().getRexBuilder(),
                algRoot.alg::getCluster,
                true );
        return algRoot.withAlg( typeFlattener.rewrite( algRoot.alg ) );
    }


    @Override
    public AlgRoot getSourceIterator( Statement statement, LogicalTable table, Map<Long, List<AllocationColumn>> placementDistribution ) {

        // Build Query
        AlgOptCluster cluster = AlgOptCluster.create(
                statement.getQueryProcessor().getPlanner(),
                new RexBuilder( statement.getTransaction().getTypeFactory() ), null, statement.getDataContext().getSnapshot() );

        AlgNode node = RoutingManager.getInstance().getFallbackRouter().buildJoinedScan( statement, cluster, table, placementDistribution );
        return AlgRoot.of( node, Kind.SELECT );
    }


    /*public AlgRoot getSourceIterator( Statement statement, Map<Long, List<AllocationColumn>> placement ) {

        // Build Query
        AlgOptCluster cluster = AlgOptCluster.create(
                statement.getQueryProcessor().getPlanner(),
                new RexBuilder( statement.getTransaction().getTypeFactory() ), null, statement.getDataContext().getSnapshot() );

        AlgNode node = RoutingManager.getInstance().getFallbackRouter().buildJoinedScan( statement, cluster, placement );
        return AlgRoot.of( node, Kind.SELECT );
    }*/


    public static List<AllocationColumn> selectSourcePlacements( LogicalTable table, List<LogicalColumn> columns, long excludingAdapterId ) {
        // Find the adapter with the most column placements
        Snapshot snapshot = Catalog.snapshot();
        long adapterIdWithMostPlacements = -1;
        int numOfPlacements = 0;
        for ( Entry<Long, List<Long>> entry : snapshot.alloc().getColumnPlacementsByAdapter( table.id ).entrySet() ) {
            if ( entry.getKey() != excludingAdapterId && entry.getValue().size() > numOfPlacements ) { // todo these are potentially not the most relevant ones
                adapterIdWithMostPlacements = entry.getKey();
                numOfPlacements = entry.getValue().size();
            }
        }

        List<Long> columnIds = columns.stream().map( c -> c.id ).collect( Collectors.toList() );

        // Take the adapter with most placements as base and add missing column placements
        List<AllocationColumn> placementList = new ArrayList<>();
        for ( LogicalColumn column : snapshot.rel().getColumns( table.id ) ) {
            if ( columnIds.contains( column.id ) ) {
                AllocationPlacement placement = snapshot.alloc().getPlacement( adapterIdWithMostPlacements, table.id ).orElseThrow();
                if ( snapshot.alloc().getColumn( placement.id, column.id ).isPresent() ) {
                    placementList.add( snapshot.alloc().getColumn( placement.id, column.id ).orElseThrow() );
                } else {
                    for ( AllocationColumn allocationColumn : snapshot.alloc().getColumnFromLogical( column.id ).orElseThrow() ) {
                        if ( allocationColumn.adapterId != excludingAdapterId ) {
                            placementList.add( allocationColumn );
                            break;
                        }
                    }
                }
            }
        }

        return placementList;
    }


    /**
     * Currently used to to transfer data if partitioned table is about to be merged.
     * For Table Partitioning use {@link DataMigrator#copyAllocationData(Transaction, CatalogAdapter, AllocationPlacement, PartitionProperty, List, LogicalTable)}  } instead
     *
     * @param transaction Transactional scope
     * @param store Target Store where data should be migrated to
     * @param sourceTable Source Table from where data is queried
     * @param targetTable Source Table from where data is queried
     * @param columns Necessary columns on target
     * @param placementDistribution Pre-computed mapping of partitions and the necessary column placements
     * @param targetPartitionIds Target Partitions where data should be inserted
     */
    @Override
    public void copySelectiveData( Transaction transaction, CatalogAdapter store, LogicalTable sourceTable, LogicalTable targetTable, List<LogicalColumn> columns, Map<Long, List<AllocationColumn>> placementDistribution, List<Long> targetPartitionIds ) {
        LogicalPrimaryKey sourcePrimaryKey = Catalog.getInstance().getSnapshot().rel().getPrimaryKey( sourceTable.primaryKey ).orElseThrow();
        AllocSnapshot snapshot = Catalog.getInstance().getSnapshot().alloc();

        // Check Lists
        List<AllocationColumn> targetColumnPlacements = new LinkedList<>();
        for ( LogicalColumn logicalColumn : columns ) {
            targetColumnPlacements.add( snapshot.getColumn( store.id, logicalColumn.id ).orElseThrow() );
        }

        List<LogicalColumn> selectColumnList = new LinkedList<>( columns );

        // Add primary keys to select column list
        for ( long cid : sourcePrimaryKey.columnIds ) {
            LogicalColumn logicalColumn = Catalog.getInstance().getSnapshot().rel().getColumn( cid ).orElseThrow();
            if ( !selectColumnList.contains( logicalColumn ) ) {
                selectColumnList.add( logicalColumn );
            }
        }

        Statement sourceStatement = transaction.createStatement();
        Statement targetStatement = transaction.createStatement();

        AlgRoot sourceAlg = getSourceIterator( sourceStatement, sourceTable, placementDistribution );
        AlgRoot targetAlg;
        AllocationTable allocation = snapshot.getEntity( targetPartitionIds.get( 0 ) ).map( a -> a.unwrap( AllocationTable.class ) ).orElseThrow();
        if ( allocation.getColumns().size() == columns.size() ) {
            // There have been no placements for this table on this store before. Build insert statement
            targetAlg = buildInsertStatement( targetStatement, targetColumnPlacements, allocation );
        } else {
            // Build update statement
            targetAlg = buildUpdateStatement( targetStatement, targetColumnPlacements, allocation );
        }

        // Execute Query
        try {
            PolyImplementation<Object> result = sourceStatement.getQueryProcessor().prepareQuery( sourceAlg, sourceAlg.alg.getCluster().getTypeFactory().builder().build(), true, false, false );
            final Enumerable<Object> enumerable = result.enumerable( sourceStatement.getDataContext() );
            Iterator<Object> sourceIterator = enumerable.iterator();

            Map<Long, Integer> resultColMapping = new HashMap<>();
            for ( LogicalColumn logicalColumn : selectColumnList ) {
                int i = 0;
                for ( AlgDataTypeField metaData : result.getRowType().getFieldList() ) {
                    if ( metaData.getName().equalsIgnoreCase( logicalColumn.name ) ) {
                        resultColMapping.put( logicalColumn.id, i );
                    }
                    i++;
                }
            }

            int batchSize = RuntimeConfig.DATA_MIGRATOR_BATCH_SIZE.getInteger();
            while ( sourceIterator.hasNext() ) {
                List<List<PolyValue>> rows = MetaImpl.collect(
                        result.getCursorFactory(),
                        LimitIterator.of( sourceIterator, batchSize ),
                        new ArrayList<>() ).stream().map( o -> o.stream().map( e -> (PolyValue) e ).collect( Collectors.toList() ) ).collect( Collectors.toList() );
                Map<Long, List<PolyValue>> values = new HashMap<>();
                for ( List<PolyValue> list : rows ) {
                    for ( Map.Entry<Long, Integer> entry : resultColMapping.entrySet() ) {
                        if ( !values.containsKey( entry.getKey() ) ) {
                            values.put( entry.getKey(), new LinkedList<>() );
                        }
                        values.get( entry.getKey() ).add( list.get( entry.getValue() ) );
                    }
                }
                for ( Map.Entry<Long, List<PolyValue>> v : values.entrySet() ) {
                    targetStatement.getDataContext().addParameterValues( v.getKey(), null, v.getValue() );
                }
                Iterator<?> iterator = targetStatement.getQueryProcessor()
                        .prepareQuery( targetAlg, sourceAlg.validatedRowType, true, false, true )
                        .enumerable( targetStatement.getDataContext() )
                        .iterator();

                //noinspection WhileLoopReplaceableByForEach
                while ( iterator.hasNext() ) {
                    iterator.next();
                }
                targetStatement.getDataContext().resetParameterValues();
            }
        } catch ( Throwable t ) {
            throw new RuntimeException( t );
        }
    }


    /**
     * Currently used to transfer data if unpartitioned is about to be partitioned.
     * For Table Merge use {@link #copySelectiveData(Transaction, CatalogAdapter, LogicalTable, LogicalTable, List, Map, List)}   } instead
     *
     * @param transaction Transactional scope
     * @param store Target Store where data should be migrated to
     * @param sourcePlacement Source Table from where data is queried
     * @param targetProperty
     * @param targetTables Target Table where data is to be inserted
     * @param table
     */
    @Override
    public void copyAllocationData( Transaction transaction, CatalogAdapter store, AllocationPlacement sourcePlacement, PartitionProperty targetProperty, List<AllocationTable> targetTables, LogicalTable table ) {
        if ( targetTables.stream().anyMatch( t -> t.logicalId != sourcePlacement.logicalEntityId ) ) {
            throw new GenericRuntimeException( "Unsupported migration scenario. Table ID mismatch" );
        }
        Snapshot snapshot = Catalog.getInstance().getSnapshot();
        List<LogicalColumn> selectColumnList = new LinkedList<>();

        // Add partition columns to select column list
        long partitionColumnId = targetProperty.partitionColumnId;
        LogicalColumn partitionColumn = snapshot.rel().getColumn( partitionColumnId ).orElseThrow();
        if ( !selectColumnList.contains( partitionColumn ) ) {
            selectColumnList.add( partitionColumn );
        }

        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( targetProperty.partitionType );

        //We need a columnPlacement for every partition
        Map<Long, List<AllocationColumn>> placementDistribution = new HashMap<>();
        PartitionProperty sourceProperty = snapshot.alloc().getPartitionProperty( table.id ).orElseThrow();
        placementDistribution.put( sourceProperty.partitionIds.get( 0 ), selectSourcePlacements( table, selectColumnList, -1 ) );

        Statement sourceStatement = transaction.createStatement();

        //Map PartitionId to TargetStatementQueue
        Map<Long, Statement> targetStatements = new HashMap<>();

        //Creates queue of target Statements depending
        targetTables.forEach( t -> targetStatements.put( t.id, transaction.createStatement() ) );
        // targetPartitionIds.forEach( id -> targetStatements.put( id, transaction.createStatement() ) );

        Map<Long, AlgRoot> targetAlgs = new HashMap<>();

        AlgRoot sourceAlg = getSourceIterator( sourceStatement, table, placementDistribution );
        //AllocationTable allocation = snapshot.alloc().getEntity( store.id, sourceTable.id ).map( a -> a.unwrap( AllocationTable.class ) ).orElseThrow();
        /*if ( allocation.getColumns().size() == columns.size() ) {
            // There have been no placements for this table on this store before. Build insert statement
            targetPartitionIds.forEach( id -> targetAlgs.put( id, buildInsertStatement( targetStatements.get( id ), targetColumnPlacements, allocation ) ) );
        } else {
            // Build update statement
            targetPartitionIds.forEach( id -> targetAlgs.put( id, buildUpdateStatement( targetStatements.get( id ), targetColumnPlacements, allocation ) ) );
        }*/

        // Execute Query
        try {
            PolyImplementation<PolyValue> result = sourceStatement.getQueryProcessor().prepareQuery( sourceAlg, sourceAlg.alg.getCluster().getTypeFactory().builder().build(), true, false, false );
            final Enumerable<PolyValue> enumerable = result.enumerable( sourceStatement.getDataContext() );
            Iterator<PolyValue> sourceIterator = enumerable.iterator();

            /*Map<Long, Integer> resultColMapping = new HashMap<>();
            for ( LogicalColumn logicalColumn : selectColumnList ) {
                int i = 0;
                for ( AlgDataTypeField metaData : result.getRowType().getFieldList() ) {
                    if ( metaData.getName().equalsIgnoreCase( logicalColumn.name ) ) {
                        resultColMapping.put( logicalColumn.id, i );
                    }
                    i++;
                }
            }*/
            int columIndex = 0; // todo dl //snapshot.alloc().getC sourcePlacement.indexOf( targetProperty.partitionColumnId );

            //int partitionColumnIndex = -1;
            String parsedValue = null;
            /*if ( targetTables.size() > 1 ) {
                if ( resultColMapping.containsKey( targetProperty.partitionColumnId ) ) {
                    partitionColumnIndex = resultColMapping.get( targetProperty.partitionColumnId );
                } else {
                    parsedValue = PartitionManager.NULL_STRING;
                }
            }*/

            int batchSize = RuntimeConfig.DATA_MIGRATOR_BATCH_SIZE.getInteger();
            while ( sourceIterator.hasNext() ) {
                List<List<PolyValue>> rows = result.getRows( sourceStatement, batchSize );//MetaImpl.collect( result.getCursorFactory(), LimitIterator.of( sourceIterator, batchSize ), new ArrayList<>() ).stream().map( r -> r.stream().map( e -> (PolyValue) e ).collect( Collectors.toList() ) ).collect( Collectors.toList() );

                Map<Long, Map<Long, List<PolyValue>>> partitionValues = new HashMap<>();

                for ( List<PolyValue> row : rows ) {
                    long currentPartitionId = -1;
                    /*if ( partitionColumnIndex >= 0 ) {
                        parsedValue = PartitionManager.NULL_STRING;
                        if ( row.get( partitionColumnIndex ) != null ) {
                            parsedValue = row.get( partitionColumnIndex ).toString();
                        }
                    }*/

                    if ( row.get( columIndex ) != null ) {
                        parsedValue = row.get( columIndex ).toString();
                    }

                    currentPartitionId = partitionManager.getTargetPartitionId( null, null, parsedValue );

                    //for ( Entry<Long, Integer> entry : resultColMapping.entrySet() ) {
                        /*if ( entry.getKey() == partitionColumn.id && !columns.contains( partitionColumn ) ) {
                            continue;
                        }*/
                    if ( !partitionValues.containsKey( currentPartitionId ) ) {
                        partitionValues.put( currentPartitionId, new HashMap<>() );
                    }
                    if ( !partitionValues.get( currentPartitionId ).containsKey( currentPartitionId ) ) {
                        partitionValues.get( currentPartitionId ).put( currentPartitionId, new LinkedList<>() );
                    }
                    partitionValues.get( currentPartitionId ).get( currentPartitionId ).add( row.get( columIndex ) );
                    //}
                }

                // Iterate over partitionValues in that way we don't even execute a statement which has no rows
                for ( Map.Entry<Long, Map<Long, List<PolyValue>>> dataOnPartition : partitionValues.entrySet() ) {
                    long partitionId = dataOnPartition.getKey();
                    Map<Long, List<PolyValue>> values = dataOnPartition.getValue();
                    Statement currentTargetStatement = targetStatements.get( partitionId );

                    for ( Map.Entry<Long, List<PolyValue>> columnDataOnPartition : values.entrySet() ) {
                        // Check partitionValue
                        currentTargetStatement.getDataContext().addParameterValues( columnDataOnPartition.getKey(), null, columnDataOnPartition.getValue() );
                    }

                    Iterator<?> iterator = currentTargetStatement.getQueryProcessor()
                            .prepareQuery( targetAlgs.get( partitionId ), sourceAlg.validatedRowType, true, false, false )
                            .enumerable( currentTargetStatement.getDataContext() )
                            .iterator();
                    //noinspection WhileLoopReplaceableByForEach
                    while ( iterator.hasNext() ) {
                        iterator.next();
                    }
                    currentTargetStatement.getDataContext().resetParameterValues();
                }
            }
        } catch ( Throwable t ) {
            throw new GenericRuntimeException( t );
        }
    }


    public void copyPartitionDataOld( Transaction transaction, CatalogAdapter store, LogicalTable sourceTable, LogicalTable targetTable, List<LogicalColumn> columns, List<Long> sourcePartitionIds, List<Long> targetPartitionIds ) {
        if ( sourceTable.id != targetTable.id ) {
            throw new RuntimeException( "Unsupported migration scenario. Table ID mismatch" );
        }
        Snapshot snapshot = Catalog.getInstance().getSnapshot();
        LogicalPrimaryKey primaryKey = snapshot.rel().getPrimaryKey( sourceTable.primaryKey ).orElseThrow();

        // Check Lists
        List<AllocationColumn> targetColumnPlacements = new LinkedList<>();
        for ( LogicalColumn logicalColumn : columns ) {
            targetColumnPlacements.add( snapshot.alloc().getColumn( store.id, logicalColumn.id ).orElseThrow() );
        }

        List<LogicalColumn> selectColumnList = new LinkedList<>( columns );

        // Add primary keys to select column list
        for ( long cid : primaryKey.columnIds ) {
            LogicalColumn logicalColumn = snapshot.rel().getColumn( cid ).orElseThrow();
            if ( !selectColumnList.contains( logicalColumn ) ) {
                selectColumnList.add( logicalColumn );
            }
        }

        PartitionProperty targetProperty = snapshot.alloc().getPartitionProperty( targetTable.id ).orElseThrow();
        // Add partition columns to select column list
        long partitionColumnId = targetProperty.partitionColumnId;
        LogicalColumn partitionColumn = snapshot.rel().getColumn( partitionColumnId ).orElseThrow();
        if ( !selectColumnList.contains( partitionColumn ) ) {
            selectColumnList.add( partitionColumn );
        }

        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( targetProperty.partitionType );

        //We need a columnPlacement for every partition
        Map<Long, List<AllocationColumn>> placementDistribution = new HashMap<>();
        PartitionProperty sourceProperty = snapshot.alloc().getPartitionProperty( sourceTable.id ).orElseThrow();
        placementDistribution.put( sourceProperty.partitionIds.get( 0 ), selectSourcePlacements( sourceTable, selectColumnList, -1 ) );

        Statement sourceStatement = transaction.createStatement();

        //Map PartitionId to TargetStatementQueue
        Map<Long, Statement> targetStatements = new HashMap<>();

        //Creates queue of target Statements depending
        targetPartitionIds.forEach( id -> targetStatements.put( id, transaction.createStatement() ) );

        Map<Long, AlgRoot> targetAlgs = new HashMap<>();

        AlgRoot sourceAlg = getSourceIterator( sourceStatement, sourceTable, placementDistribution );
        AllocationTable allocation = snapshot.alloc().getEntity( store.id, sourceTable.id ).map( a -> a.unwrap( AllocationTable.class ) ).orElseThrow();
        if ( allocation.getColumns().size() == columns.size() ) {
            // There have been no placements for this table on this store before. Build insert statement
            targetPartitionIds.forEach( id -> targetAlgs.put( id, buildInsertStatement( targetStatements.get( id ), targetColumnPlacements, allocation ) ) );
        } else {
            // Build update statement
            targetPartitionIds.forEach( id -> targetAlgs.put( id, buildUpdateStatement( targetStatements.get( id ), targetColumnPlacements, allocation ) ) );
        }

        // Execute Query
        try {
            PolyImplementation<Object> result = sourceStatement.getQueryProcessor().prepareQuery( sourceAlg, sourceAlg.alg.getCluster().getTypeFactory().builder().build(), true, false, false );
            final Enumerable<Object> enumerable = result.enumerable( sourceStatement.getDataContext() );
            Iterator<Object> sourceIterator = enumerable.iterator();

            Map<Long, Integer> resultColMapping = new HashMap<>();
            for ( LogicalColumn logicalColumn : selectColumnList ) {
                int i = 0;
                for ( AlgDataTypeField metaData : result.getRowType().getFieldList() ) {
                    if ( metaData.getName().equalsIgnoreCase( logicalColumn.name ) ) {
                        resultColMapping.put( logicalColumn.id, i );
                    }
                    i++;
                }
            }

            int partitionColumnIndex = -1;
            String parsedValue = null;
            if ( targetProperty.isPartitioned ) {
                if ( resultColMapping.containsKey( targetProperty.partitionColumnId ) ) {
                    partitionColumnIndex = resultColMapping.get( targetProperty.partitionColumnId );
                } else {
                    parsedValue = PartitionManager.NULL_STRING;
                }
            }

            int batchSize = RuntimeConfig.DATA_MIGRATOR_BATCH_SIZE.getInteger();
            while ( sourceIterator.hasNext() ) {
                List<List<PolyValue>> rows = MetaImpl.collect( result.getCursorFactory(), LimitIterator.of( sourceIterator, batchSize ), new ArrayList<>() ).stream().map( r -> r.stream().map( e -> (PolyValue) e ).collect( Collectors.toList() ) ).collect( Collectors.toList() );

                Map<Long, Map<Long, List<PolyValue>>> partitionValues = new HashMap<>();

                for ( List<PolyValue> row : rows ) {
                    long currentPartitionId = -1;
                    if ( partitionColumnIndex >= 0 ) {
                        parsedValue = PartitionManager.NULL_STRING;
                        if ( row.get( partitionColumnIndex ) != null ) {
                            parsedValue = row.get( partitionColumnIndex ).toString();
                        }
                    }

                    currentPartitionId = partitionManager.getTargetPartitionId( null, null/*targetTable*/, parsedValue );

                    for ( Map.Entry<Long, Integer> entry : resultColMapping.entrySet() ) {
                        if ( entry.getKey() == partitionColumn.id && !columns.contains( partitionColumn ) ) {
                            continue;
                        }
                        if ( !partitionValues.containsKey( currentPartitionId ) ) {
                            partitionValues.put( currentPartitionId, new HashMap<>() );
                        }
                        if ( !partitionValues.get( currentPartitionId ).containsKey( entry.getKey() ) ) {
                            partitionValues.get( currentPartitionId ).put( entry.getKey(), new LinkedList<>() );
                        }
                        partitionValues.get( currentPartitionId ).get( entry.getKey() ).add( row.get( entry.getValue() ) );
                    }
                }

                // Iterate over partitionValues in that way we don't even execute a statement which has no rows
                for ( Map.Entry<Long, Map<Long, List<PolyValue>>> dataOnPartition : partitionValues.entrySet() ) {
                    long partitionId = dataOnPartition.getKey();
                    Map<Long, List<PolyValue>> values = dataOnPartition.getValue();
                    Statement currentTargetStatement = targetStatements.get( partitionId );

                    for ( Map.Entry<Long, List<PolyValue>> columnDataOnPartition : values.entrySet() ) {
                        // Check partitionValue
                        currentTargetStatement.getDataContext().addParameterValues( columnDataOnPartition.getKey(), null, columnDataOnPartition.getValue() );
                    }

                    Iterator<?> iterator = currentTargetStatement.getQueryProcessor()
                            .prepareQuery( targetAlgs.get( partitionId ), sourceAlg.validatedRowType, true, false, false )
                            .enumerable( currentTargetStatement.getDataContext() )
                            .iterator();
                    //noinspection WhileLoopReplaceableByForEach
                    while ( iterator.hasNext() ) {
                        iterator.next();
                    }
                    currentTargetStatement.getDataContext().resetParameterValues();
                }
            }
        } catch ( Throwable t ) {
            throw new RuntimeException( t );
        }
    }

}
