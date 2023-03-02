/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.catalog.entity.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.catalog.logistic.PlacementType;

@EqualsAndHashCode(callSuper = true)
@Value
@With
public class AllocationTable extends AllocationEntity<LogicalTable> {

    public List<CatalogColumnPlacement> placements;
    public long adapterId;
    public LogicalTable logicalTable;
    public String adapterName;


    public AllocationTable( LogicalTable logicalTable, long id, String name, long adapterId, String adapterName, List<CatalogColumnPlacement> placements ) {
        super( logicalTable, id, name, EntityType.ENTITY, NamespaceType.RELATIONAL, adapterId );
        this.logicalTable = logicalTable;
        this.adapterId = adapterId;
        this.placements = placements;
        this.adapterName = adapterName;
    }


    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[0];
    }


    @Override
    public Expression asExpression() {
        return Expressions.call( Catalog.CATALOG_EXPRESSION, "getAllocTable", Expressions.constant( id ) );
    }


    public Map<Long, String> getColumnNames() {
        return null;
    }


    public Map<Long, LogicalColumn> getColumns() {
        return null;
    }


    public Map<String, Long> getColumnNamesIds() {
        return getColumnNames().entrySet().stream().collect( Collectors.toMap( Entry::getValue, Entry::getKey ) );
    }


    public String getNamespaceName() {
        return null;
    }


    public AllocationTable withAddedColumn( long columnId, PlacementType placementType, String physicalSchemaName, String physicalTableName, String physicalColumnName ) {
        List<CatalogColumnPlacement> placements = new ArrayList<>( this.placements );
        placements.add( new CatalogColumnPlacement( logical.namespaceId, id, columnId, adapterId, adapterName, placementType, physicalSchemaName, physicalColumnName, 0 ) );

        return withPlacements( placements );
    }


    public AllocationTable withRemovedColumn( long columnId ) {
        List<CatalogColumnPlacement> placements = new ArrayList<>( this.placements );
        return withPlacements( placements.stream().filter( p -> p.columnId != columnId ).collect( Collectors.toList() ) );
    }

}
