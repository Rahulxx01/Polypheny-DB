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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.routing.LogicalQueryInformation;
import org.polypheny.db.tools.RoutedAlgBuilder;
import org.polypheny.db.transaction.Statement;


@Slf4j
public class IcarusRouter extends FullPlacementQueryRouter {

    @Override
    protected List<RoutedAlgBuilder> handleHorizontalPartitioning( AlgNode node, LogicalTable table, Statement statement, List<RoutedAlgBuilder> builders, AlgOptCluster cluster, LogicalQueryInformation queryInformation ) {
        this.cancelQuery = true;
        return Collections.emptyList();
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
        // Same as no partitioning
        return handleNonePartitioning( node, table, statement, builders, cluster, queryInformation );
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

        final Set<List<AllocationColumn>> placements = selectPlacement( table, queryInformation );
        List<RoutedAlgBuilder> newBuilders = new ArrayList<>();
        if ( placements.isEmpty() ) {
            this.cancelQuery = true;
            return Collections.emptyList();
        }

        // Initial case with empty single builder
        if ( builders.size() == 1 && builders.get( 0 ).getPhysicalPlacementsOfPartitions().isEmpty() ) {
            for ( List<AllocationColumn> currentPlacement : placements ) {
                final Map<Long, List<AllocationColumn>> currentPlacementDistribution = new HashMap<>();
                PartitionProperty property = Catalog.snapshot().alloc().getPartitionProperty( table.id ).orElseThrow();
                currentPlacementDistribution.put( property.partitionIds.get( 0 ), currentPlacement );

                final RoutedAlgBuilder newBuilder = RoutedAlgBuilder.createCopy( statement, cluster, builders.get( 0 ) );
                newBuilder.addPhysicalInfo( currentPlacementDistribution );
                newBuilder.push( super.buildJoinedScan( statement, cluster, table, null ) );
                newBuilders.add( newBuilder );
            }
        } else {
            // Already one placement added
            // Add placement in order of list to combine full placements of one storeId
            if ( placements.size() != builders.size() ) {
                log.error( "Not allowed! With Icarus, this should not happen" );
                throw new GenericRuntimeException( "Not allowed! With Icarus, this should not happen" );
            }

            for ( List<AllocationColumn> currentPlacement : placements ) {
                final Map<Long, List<AllocationColumn>> currentPlacementDistribution = new HashMap<>();
                PartitionProperty property = Catalog.snapshot().alloc().getPartitionProperty( table.id ).orElseThrow();
                currentPlacementDistribution.put( property.partitionIds.get( 0 ), currentPlacement );

                // AdapterId for all col placements same
                final long placementId = currentPlacement.get( 0 ).placementId;

                // Find corresponding builder:
                final RoutedAlgBuilder builder = builders.stream().filter( b -> {
                    final List<AllocationColumn> listPairs = b.getPhysicalPlacementsOfPartitions().values().stream()
                            .flatMap( Collection::stream )
                            .collect( Collectors.toList() );
                    final Optional<Long> found = listPairs.stream()
                            .map( elem -> elem.placementId )
                            .filter( elem -> elem == placementId )
                            .findFirst();
                            return found.isPresent();
                        }
                ).findAny().orElse( null );

                if ( builder == null ) {
                    // If builder not found, adapter with id will be removed.
                    continue;
                }

                final RoutedAlgBuilder newBuilder = RoutedAlgBuilder.createCopy( statement, cluster, builder );
                newBuilder.addPhysicalInfo( currentPlacementDistribution );
                newBuilder.push( super.buildJoinedScan( statement, cluster, table, null ) );
                newBuilders.add( newBuilder );
            }
            if ( newBuilders.isEmpty() ) {
                // apparently we have a problem and no builder fits
                cancelQuery = true;
                log.error( "Icarus did not find a suitable builder!" );
                return Collections.emptyList();
            }

        }

        builders.clear();
        builders.addAll( newBuilders );

        return builders;
    }


}
