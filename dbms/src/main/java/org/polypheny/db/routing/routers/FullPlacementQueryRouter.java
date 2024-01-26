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

package org.polypheny.db.routing.routers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.allocation.AllocationPlacement;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.partition.PartitionManager;
import org.polypheny.db.partition.PartitionManagerFactory;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.routing.LogicalQueryInformation;
import org.polypheny.db.routing.Router;
import org.polypheny.db.routing.factories.RouterFactory;
import org.polypheny.db.tools.RoutedAlgBuilder;
import org.polypheny.db.transaction.Statement;


@Slf4j
public class FullPlacementQueryRouter extends AbstractDqlRouter {

    @Override
    protected List<RoutedAlgBuilder> handleHorizontalPartitioning(
            AlgNode node,
            LogicalTable table,
            Statement statement,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation ) {

        if ( log.isDebugEnabled() ) {
            log.debug( "{} is horizontally partitioned", table.name );
        }

        List<Map<Long, List<AllocationColumn>>> placements = selectPlacementHorizontalPartitioning( node, table, queryInformation );

        List<RoutedAlgBuilder> newBuilders = new ArrayList<>();
        for ( Map<Long, List<AllocationColumn>> placementCombination : placements ) {
            for ( RoutedAlgBuilder builder : builders ) {
                RoutedAlgBuilder newBuilder = RoutedAlgBuilder.createCopy( statement, cluster, builder );
                newBuilder.addPhysicalInfo( placementCombination );
                newBuilder.push( super.buildJoinedScan( statement, cluster, table, placementCombination ) );
                newBuilders.add( newBuilder );
            }
        }

        builders.clear();
        builders.addAll( newBuilders );

        return builders;
    }


    @Override
    protected List<RoutedAlgBuilder> handleVerticalPartitioningOrReplication(
            AlgNode node,
            LogicalTable table,
            Statement statement,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation ) {
        cancelQuery = true;

        return Collections.emptyList();

        // Same as no partitioning
        //return handleNonePartitioning( node, table, statement, builders, cluster, queryInformation );
    }


    @Override
    protected List<RoutedAlgBuilder> handleNonePartitioning(
            AlgNode node,
            LogicalTable table,
            Statement statement,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation ) {

        if ( log.isDebugEnabled() ) {
            log.debug( "{} is NOT partitioned - Routing will be easy", table.name );
        }

        List<RoutedAlgBuilder> newBuilders = new ArrayList<>();

        List<AllocationPlacement> placements = Catalog.snapshot().alloc().getPlacementsFromLogical( table.id );
        List<AllocationEntity> allocs = Catalog.snapshot().alloc().getAllocsOfPlacement( placements.get( 0 ).id );
        List<AllocationColumn> columns = Catalog.snapshot().alloc().getColumns( allocs.get( 0 ).placementId );

        for ( RoutedAlgBuilder builder : builders ) {
            RoutedAlgBuilder newBuilder = RoutedAlgBuilder.createCopy( statement, cluster, builder );
            newBuilder.addPhysicalInfo( Map.of( allocs.get( 0 ).partitionId, columns ) );
            newBuilder.push( super.buildJoinedScan( statement, cluster, table, Map.of( allocs.get( 0 ).placementId, allocs.get( 0 ).unwrap( AllocationTable.class ).orElseThrow().getColumns() ) ) );
            newBuilders.add( newBuilder );
        }
        //}

        builders.clear();
        builders.addAll( newBuilders );

        return builders;
    }


    protected List<Map<Long, List<AllocationColumn>>> selectPlacementHorizontalPartitioning( AlgNode node, LogicalTable table, LogicalQueryInformation queryInformation ) {
        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionProperty property = Catalog.snapshot().alloc().getPartitionProperty( table.id ).orElseThrow();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( property.partitionType );

        // Utilize scanId to retrieve Partitions being accessed
        List<Long> partitionIds = queryInformation.getAccessedPartitions().get( node.getEntity().id );

        Map<Long, Map<Long, List<AllocationColumn>>> allPlacements = partitionManager.getAllPlacements( table, partitionIds );

        return List.copyOf( allPlacements.values() );
    }


    protected Set<List<AllocationColumn>> selectPlacement( LogicalTable table, LogicalQueryInformation queryInformation ) {
        // Get used columns from analyze
        List<Long> usedColumns = queryInformation.getAllColumnsPerTable( table.id );

        // Filter for placements by adapters
        List<AllocationEntity> allocs = Catalog.snapshot().alloc().getFromLogical( table.id ).stream()
                .map( a -> a.unwrap( AllocationTable.class ).orElseThrow() )
                .filter( a -> new HashSet<>( a.getColumnIds() ).containsAll( usedColumns ) )
                .collect( Collectors.toList() );

        /*List<Long> adapters = Catalog.snapshot().alloc().getColumnPlacementsByAdapters( catalogTable.id ).entrySet()
                .stream()
                .filter( elem -> new HashSet<>( elem.getValue() ).containsAll( usedColumns ) )
                .map( Entry::getKey )
                .collect( Collectors.toList() );*/

        final Set<List<AllocationColumn>> result = new HashSet<>();
        for ( AllocationEntity alloc : allocs ) {
            List<AllocationColumn> placements = usedColumns.stream()
                    .map( colId -> alloc.unwrap( AllocationTable.class ).orElseThrow().getColumns().stream().filter( c -> c.columnId == colId ).findFirst().orElseThrow() )
                    .toList();

            if ( !placements.isEmpty() ) {
                result.add( placements );
            } else {
                // no available placements found
                this.cancelQuery = true;
            }
        }

        return result;
    }


    public static class FullPlacementQueryRouterFactory extends RouterFactory {

        @Override
        public Router createInstance() {
            return new FullPlacementQueryRouter();
        }

    }

}
