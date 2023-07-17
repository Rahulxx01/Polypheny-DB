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

package org.polypheny.db.catalog.catalogs;

import com.google.common.collect.ImmutableList;
import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.catalog.IdBuilder;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.allocation.AllocationTableWrapper;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.entity.physical.PhysicalColumn;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalTable;
import org.polypheny.db.schema.Namespace;
import org.polypheny.db.util.Pair;

@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class RelStoreCatalog extends StoreCatalog {


    @Serialize
    ConcurrentMap<Long, Namespace> namespaces;
    @Serialize
    ConcurrentMap<Long, PhysicalTable> tables;
    @Serialize
    ConcurrentMap<Long, PhysicalColumn> columns;

    ConcurrentMap<Long, Pair<AllocationEntity, List<Long>>> allocRelations;


    public RelStoreCatalog( long adapterId ) {
        this( adapterId, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>() );
    }


    public RelStoreCatalog(
            @Deserialize("adapterId") long adapterId,
            @Deserialize("namespaces") Map<Long, Namespace> namespaces,
            @Deserialize("tables") Map<Long, PhysicalTable> tables,
            @Deserialize("columns") Map<Long, PhysicalColumn> columns,
            @Deserialize("allocRelations") Map<Long, Pair<AllocationEntity, List<Long>>> allocRelations ) {
        super( adapterId );
        this.namespaces = new ConcurrentHashMap<>( namespaces );
        this.tables = new ConcurrentHashMap<>( tables );
        this.columns = new ConcurrentHashMap<>( columns );

        this.allocRelations = new ConcurrentHashMap<>( allocRelations );
    }


    public void addTable( PhysicalTable table ) {
        tables.put( table.id, table );
    }


    public void addColumn( PhysicalColumn column ) {
        columns.put( column.id, column );
    }


    public PhysicalTable getTable( long id ) {
        return tables.get( id );
    }


    public PhysicalColumn getColumn( long id ) {
        return columns.get( id );
    }


    public Namespace getNamespace( long id ) {
        return namespaces.get( id );
    }


    public void addNamespace( long id, Namespace namespace ) {
        namespaces.put( id, namespace );
    }


    public PhysicalTable createTable( String namespaceName, String tableName, Map<Long, String> columnNames, LogicalTable logical, Map<Long, LogicalColumn> lColumns, AllocationTableWrapper wrapper ) {
        AllocationTable allocation = wrapper.table;
        List<AllocationColumn> columns = wrapper.columns;
        List<PhysicalColumn> pColumns = columns.stream().map( c -> new PhysicalColumn( columnNames.get( c.columnId ), logical.id, allocation.adapterId, c.position, lColumns.get( c.columnId ) ) ).collect( Collectors.toList() );
        PhysicalTable table = new PhysicalTable( IdBuilder.getInstance().getNewPhysicalId(), allocation.id, tableName, pColumns, logical.namespaceId, namespaceName, allocation.adapterId );
        addTable( table );
        pColumns.forEach( this::addColumn );
        allocRelations.put( allocation.id, Pair.of( allocation, List.of( table.id ) ) );
        return table;
    }


    public PhysicalColumn addColumn( String name, long allocId, long adapterId, int position, LogicalColumn lColumn ) {
        PhysicalColumn column = new PhysicalColumn( name, allocId, adapterId, position, lColumn );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> columns = new ArrayList<>( table.columns );
        columns.add( position - 1, column );
        addColumn( column );
        tables.put( table.id, table.toBuilder().columns( ImmutableList.copyOf( columns ) ).build() );
        return column;
    }


    public PhysicalColumn updateColumnType( long allocId, LogicalColumn newCol ) {
        PhysicalColumn old = getColumn( newCol.id );
        PhysicalColumn column = new PhysicalColumn( old.name, allocId, adapterId, old.position, newCol );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> pColumn = new ArrayList<>( table.columns );
        pColumn.remove( old );
        pColumn.add( column );
        tables.put( table.id, table.toBuilder().columns( ImmutableList.copyOf( pColumn ) ).build() );
        return column;
    }


    public PhysicalTable fromAllocation( long id ) {
        return tables.get( allocRelations.get( id ).getValue().get( 0 ) );
    }


    @Override
    public PhysicalEntity getPhysical( long id ) {
        return tables.get( id );
    }


    public void dropTable( long id ) {
        for ( long allocId : allocRelations.get( id ).right ) {
            for ( PhysicalColumn column : tables.get( allocId ).columns ) {
                columns.remove( column.id );
            }
            tables.remove( allocId );
        }
        allocRelations.remove( id );

    }


    public void dropColum( long allocId, long columnId ) {
        PhysicalColumn column = columns.get( columnId );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> pColumn = new ArrayList<>( table.columns );
        pColumn.remove( column );
        tables.put( table.id, table.toBuilder().columns( ImmutableList.copyOf( pColumn ) ).build() );
        columns.remove( columnId );
    }

}
