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

package org.polypheny.db.catalog.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.catalog.logistic.DataPlacementRole;
import org.polypheny.db.catalog.logistic.PlacementType;


/**
 * Serves as a container, which holds all information related to a table entity placed on physical store.
 */
@SuperBuilder(toBuilder = true)
@Value
@Deprecated
public class CatalogDataPlacement implements CatalogObject {

    private static final long serialVersionUID = 5192378654968316873L;
    public long tableId;
    public long adapterId;

    public PlacementType placementType;

    // Is present at the DataPlacement && the PartitionPlacement
    // Although, partitionPlacements are those that get effectively updated
    // A DataPlacement can directly forbid that any Placements within this DataPlacement container can get outdated.
    // Therefore, the role at the DataPlacement specifies if underlying placements can even be outdated.
    public DataPlacementRole dataPlacementRole;

    public ImmutableList<Long> columnPlacementsOnAdapter;

    // Serves as a pre-aggregation to apply filters more easily. In that case reads are more important
    // and frequent than writes
    public ImmutableMap<DataPlacementRole, ImmutableList<Long>> partitionPlacementsOnAdapterByRole;


    // The newest commit timestamp when any partitions inside this placement has been updated or refreshed
    // Equals the newest timestamp ony any of the LogicalPartitionPlacements.
    // Technically other  linked attachments could still have older update timestamps.
    // This should help to quickly identify placements that can fulfil certain conditions.
    // Without having to traverse all partition placements one-by-one
    @NonFinal
    public Timestamp updateTimestamp;


    public CatalogDataPlacement(
            long tableId,
            int adapterId,
            PlacementType placementType,
            DataPlacementRole dataPlacementRole,
            @NonNull final List<Long> columnPlacementsOnAdapter,
            @NonNull final List<Long> partitionPlacementsOnAdapter ) {
        this.tableId = tableId;
        this.adapterId = adapterId;
        this.placementType = placementType;
        this.dataPlacementRole = dataPlacementRole;
        this.columnPlacementsOnAdapter = ImmutableList.copyOf( columnPlacementsOnAdapter );
        this.partitionPlacementsOnAdapterByRole = ImmutableMap.copyOf( structurizeDataPlacements( partitionPlacementsOnAdapter ) );

    }


    @SneakyThrows
    public String getTableName() {
        throw new RuntimeException( "" );
    }


    @SneakyThrows
    public String getAdapterName() {
        // return Catalog.getInstance().getAdapter( adapterId ).uniqueName;
        throw new NotImplementedException();
    }


    public boolean hasFullPlacement() {
        return hasColumnFullPlacement() && hasPartitionFullPlacement();
    }


    public boolean hasColumnFullPlacement() {
        throw new NotImplementedException();
        // return Catalog.getInstance().getTable( this.tableId ).fieldIds.size() == columnPlacementsOnAdapter.size();
    }


    public boolean hasPartitionFullPlacement() {
        throw new NotImplementedException();
        //return Catalog.getInstance().getTable( this.tableId ).partitionProperty.partitionIds.size() == getAllPartitionIds().size();
    }


    public List<Long> getAllPartitionIds() {
        return partitionPlacementsOnAdapterByRole.values()
                .stream()
                .flatMap( List::stream )
                .collect( Collectors.toList() );
    }


    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[0];
    }


    private Map<DataPlacementRole, ImmutableList<Long>> structurizeDataPlacements( @NonNull final List<Long> unsortedPartitionIds ) {
        // Since this shall only be called after initialization of dataPlacement object,
        // we need to clear the contents of partitionPlacementsOnAdapterByRole
        /*Map<DataPlacementRole, ImmutableList<Long>> partitionsPerRole = new HashMap<>();

        try {
            Catalog catalog = Catalog.getInstance();
            if ( !unsortedPartitionIds.isEmpty() ) {
                CatalogPartitionPlacement partitionPlacement;
                for ( long partitionId : unsortedPartitionIds ) {
                    partitionPlacement = catalog.getPartitionPlacement( this.adapterId, partitionId );
                    DataPlacementRole role = partitionPlacement.role;

                    List<Long> partitions = new ArrayList<>();
                    if ( partitionsPerRole.containsKey( role ) ) {
                        // Get contents of List and add partition to it
                        partitions = new ArrayList<>( partitionsPerRole.get( role ) );
                    } else {
                        partitionsPerRole.put( role, ImmutableList.copyOf( new ArrayList<>() ) );
                    }
                    partitions.add( partitionId );
                    partitionsPerRole.replace( role, ImmutableList.copyOf( partitions ) );
                }
            }
        } catch ( RuntimeException e ) {
            // Catalog is not ready
            // Happens only for defaultColumns during setAndGetInstance of Catalog
            // Just assume UPTODATE for all.
            partitionsPerRole.put( DataPlacementRole.UPTODATE, ImmutableList.copyOf( unsortedPartitionIds ) );
        }

        // Finally, overwrite entire partitionPlacementsOnAdapterByRole at Once
        return partitionsPerRole;*/
        throw new NotImplementedException();
    }

}
