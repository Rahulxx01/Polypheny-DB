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

package org.polypheny.db.cypher.ddl;


import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.polypheny.db.StatisticsManager;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.adapter.DataSource;
import org.polypheny.db.adapter.DataSource.ExportedColumn;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.DataStore.AvailableIndexMethod;
import org.polypheny.db.adapter.index.IndexManager;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.BiAlg;
import org.polypheny.db.algebra.SingleAlg;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.logical.relational.LogicalScan;
import org.polypheny.db.algebra.logical.relational.LogicalViewScan;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.Collation;
import org.polypheny.db.catalog.Catalog.ConstraintType;
import org.polypheny.db.catalog.Catalog.DataPlacementRole;
import org.polypheny.db.catalog.Catalog.EntityType;
import org.polypheny.db.catalog.Catalog.ForeignKeyOption;
import org.polypheny.db.catalog.Catalog.IndexType;
import org.polypheny.db.catalog.Catalog.NamespaceType;
import org.polypheny.db.catalog.Catalog.PartitionType;
import org.polypheny.db.catalog.Catalog.PlacementType;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.catalog.NameGenerator;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.CatalogAdapter.AdapterType;
import org.polypheny.db.catalog.entity.CatalogCollection;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogConstraint;
import org.polypheny.db.catalog.entity.CatalogDataPlacement;
import org.polypheny.db.catalog.entity.CatalogDocumentMapping;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.catalog.entity.CatalogForeignKey;
import org.polypheny.db.catalog.entity.CatalogGraphDatabase;
import org.polypheny.db.catalog.entity.CatalogGraphMapping;
import org.polypheny.db.catalog.entity.CatalogGraphPlacement;
import org.polypheny.db.catalog.entity.CatalogIndex;
import org.polypheny.db.catalog.entity.CatalogKey;
import org.polypheny.db.catalog.entity.CatalogMaterializedView;
import org.polypheny.db.catalog.entity.CatalogNamespace;
import org.polypheny.db.catalog.entity.CatalogPartitionGroup;
import org.polypheny.db.catalog.entity.CatalogPrimaryKey;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.entity.CatalogView;
import org.polypheny.db.catalog.entity.MaterializedCriteria;
import org.polypheny.db.catalog.entity.MaterializedCriteria.CriteriaType;
import org.polypheny.db.catalog.exceptions.ColumnAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.EntityAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.NamespaceAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.UnknownAdapterException;
import org.polypheny.db.catalog.exceptions.UnknownCollationException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.catalog.exceptions.UnknownConstraintException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownForeignKeyException;
import org.polypheny.db.catalog.exceptions.UnknownGraphException;
import org.polypheny.db.catalog.exceptions.UnknownIndexException;
import org.polypheny.db.catalog.exceptions.UnknownKeyException;
import org.polypheny.db.catalog.exceptions.UnknownNamespaceException;
import org.polypheny.db.catalog.exceptions.UnknownPartitionTypeException;
import org.polypheny.db.catalog.exceptions.UnknownTableException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.cypher.ddl.exception.AlterSourceException;
import org.polypheny.db.cypher.ddl.exception.ColumnNotExistsException;
import org.polypheny.db.cypher.ddl.exception.DdlOnSourceException;
import org.polypheny.db.cypher.ddl.exception.IndexExistsException;
import org.polypheny.db.cypher.ddl.exception.IndexPreventsRemovalException;
import org.polypheny.db.cypher.ddl.exception.LastPlacementException;
import org.polypheny.db.cypher.ddl.exception.MissingColumnPlacementException;
import org.polypheny.db.cypher.ddl.exception.NotMaterializedViewException;
import org.polypheny.db.cypher.ddl.exception.NotNullAndDefaultValueException;
import org.polypheny.db.cypher.ddl.exception.NotViewException;
import org.polypheny.db.cypher.ddl.exception.PartitionGroupNamesNotUniqueException;
import org.polypheny.db.cypher.ddl.exception.PlacementAlreadyExistsException;
import org.polypheny.db.cypher.ddl.exception.PlacementIsPrimaryException;
import org.polypheny.db.cypher.ddl.exception.PlacementNotExistsException;
import org.polypheny.db.cypher.ddl.exception.SchemaNotExistException;
import org.polypheny.db.cypher.ddl.exception.UnknownIndexMethodException;
import org.polypheny.db.monitoring.events.DdlEvent;
import org.polypheny.db.monitoring.events.StatementEvent;
import org.polypheny.db.partition.PartitionManager;
import org.polypheny.db.partition.PartitionManagerFactory;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.partition.properties.TemperaturePartitionProperty;
import org.polypheny.db.partition.properties.TemperaturePartitionProperty.PartitionCostIndication;
import org.polypheny.db.partition.raw.RawTemperaturePartitionInformation;
import org.polypheny.db.processing.DataMigrator;
import org.polypheny.db.routing.RoutingManager;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.runtime.PolyphenyDbException;
import org.polypheny.db.schema.LogicalTable;
import org.polypheny.db.schema.PolySchemaBuilder;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.type.ArrayType;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.view.MaterializedViewManager;


@Slf4j
public class DdlManagerImpl extends DdlManager {

    private final Catalog catalog;


    public DdlManagerImpl( Catalog catalog ) {
        this.catalog = catalog;
    }


    private void checkIfDdlPossible( EntityType entityType ) throws DdlOnSourceException {
        if ( entityType == EntityType.SOURCE ) {
            throw new DdlOnSourceException();
        }
    }


    private void checkViewDependencies( CatalogEntity catalogEntity ) {
        if ( catalogEntity.connectedViews.size() > 0 ) {
            List<String> views = new ArrayList<>();
            for ( Long id : catalogEntity.connectedViews ) {
                views.add( catalog.getTable( id ).name );
            }
            throw new PolyphenyDbException( "Cannot alter table because of underlying View " + views.stream().map( String::valueOf ).collect( Collectors.joining( (", ") ) ) );
        }
    }


    private void addDefaultValue( String defaultValue, long addedColumnId ) {
        if ( defaultValue != null ) {
            // TODO: String is only a temporal solution for default values
            String v = defaultValue;
            if ( v.startsWith( "'" ) ) {
                v = v.substring( 1, v.length() - 1 );
            }
            catalog.setDefaultValue( addedColumnId, PolyType.VARCHAR, v );
        }
    }


    protected DataStore getDataStoreInstance( int storeId ) throws DdlOnSourceException {
        Adapter adapterInstance = AdapterManager.getInstance().getAdapter( storeId );
        if ( adapterInstance == null ) {
            throw new RuntimeException( "Unknown store id: " + storeId );
        }
        // Make sure it is a data store instance
        if ( adapterInstance instanceof DataStore ) {
            return (DataStore) adapterInstance;
        } else if ( adapterInstance instanceof DataSource ) {
            throw new DdlOnSourceException();
        } else {
            throw new RuntimeException( "Unknown kind of adapter: " + adapterInstance.getClass().getName() );
        }
    }


    private CatalogColumn getCatalogColumn( long tableId, String columnName ) throws ColumnNotExistsException {
        try {
            return catalog.getField( tableId, columnName );
        } catch ( UnknownColumnException e ) {
            throw new ColumnNotExistsException( tableId, columnName );
        }
    }


    @Override
    public long createNamespace( String name, long databaseId, NamespaceType type, int userId, boolean ifNotExists, boolean replace ) throws NamespaceAlreadyExistsException {
        // Check if there is already a schema with this name
        if ( catalog.checkIfExistsNamespace( databaseId, name ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a schema with this name because "IF NOT EXISTS" was specified
                try {
                    return catalog.getNamespace( Catalog.defaultDatabaseId, name ).id;
                } catch ( UnknownNamespaceException e ) {
                    throw new RuntimeException( "The catalog seems to be corrupt, as it was impossible to retrieve an existing namespace." );
                }
            } else if ( replace ) {
                throw new RuntimeException( "Replacing namespace is not yet supported." );
            } else {
                throw new NamespaceAlreadyExistsException();
            }
        } else {
            return catalog.addNamespace( name, databaseId, userId, type );
        }
    }


    @Override
    public void addAdapter( String adapterName, String clazzName, Map<String, String> config ) {
        Adapter adapter = AdapterManager.getInstance().addAdapter( clazzName, adapterName, config );
        if ( adapter instanceof DataSource ) {
            Map<String, List<ExportedColumn>> exportedColumns;
            try {
                exportedColumns = ((DataSource) adapter).getExportedColumns();
            } catch ( Exception e ) {
                AdapterManager.getInstance().removeAdapter( adapter.getAdapterId() );
                throw new RuntimeException( "Could not deploy adapter", e );
            }
            // Create table, columns etc.
            for ( Map.Entry<String, List<ExportedColumn>> entry : exportedColumns.entrySet() ) {
                // Make sure the table name is unique
                String tableName = entry.getKey();
                if ( catalog.checkIfExistsEntity( 1, tableName ) ) {
                    int i = 0;
                    while ( catalog.checkIfExistsEntity( 1, tableName + i ) ) {
                        i++;
                    }
                    tableName += i;
                }

                long tableId = catalog.addEntity( tableName, 1, 1, EntityType.SOURCE, !((DataSource) adapter).isDataReadOnly() );
                List<Long> primaryKeyColIds = new ArrayList<>();
                int colPos = 1;
                String physicalSchemaName = null;
                String physicalTableName = null;
                for ( ExportedColumn exportedColumn : entry.getValue() ) {
                    long columnId = catalog.addColumn(
                            exportedColumn.name,
                            tableId,
                            colPos++,
                            exportedColumn.type,
                            exportedColumn.collectionsType,
                            exportedColumn.length,
                            exportedColumn.scale,
                            exportedColumn.dimension,
                            exportedColumn.cardinality,
                            exportedColumn.nullable,
                            Collation.getDefaultCollation() );
                    catalog.addColumnPlacement(
                            adapter.getAdapterId(),
                            columnId,
                            PlacementType.STATIC,
                            exportedColumn.physicalSchemaName,
                            exportedColumn.physicalTableName,
                            exportedColumn.physicalColumnName
                    ); // Not a valid partitionGroupID --> placeholder
                    catalog.updateColumnPlacementPhysicalPosition( adapter.getAdapterId(), columnId, exportedColumn.physicalPosition );
                    if ( exportedColumn.primary ) {
                        primaryKeyColIds.add( columnId );
                    }
                    if ( physicalSchemaName == null ) {
                        physicalSchemaName = exportedColumn.physicalSchemaName;
                    }
                    if ( physicalTableName == null ) {
                        physicalTableName = exportedColumn.physicalTableName;
                    }
                }
                try {
                    catalog.addPrimaryKey( tableId, primaryKeyColIds );
                    CatalogEntity catalogEntity = catalog.getTable( tableId );
                    catalog.addPartitionPlacement(
                            adapter.getAdapterId(),
                            catalogEntity.id,
                            catalogEntity.partitionProperty.partitionIds.get( 0 ),
                            PlacementType.AUTOMATIC,
                            physicalSchemaName,
                            physicalTableName,
                            DataPlacementRole.UPTODATE );
                } catch ( GenericCatalogException e ) {
                    throw new RuntimeException( "Exception while adding primary key" );
                }
            }
        }
    }


    @Override
    public void dropAdapter( String name, Statement statement ) throws UnknownAdapterException {
        if ( name.startsWith( "'" ) ) {
            name = name.substring( 1 );
        }
        if ( name.endsWith( "'" ) ) {
            name = StringUtils.chop( name );
        }

        CatalogAdapter catalogAdapter = catalog.getAdapter( name );
        if ( catalogAdapter.type == AdapterType.SOURCE ) {
            Set<Long> tablesToDrop = new HashSet<>();
            for ( CatalogColumnPlacement ccp : catalog.getColumnPlacementsOnAdapter( catalogAdapter.id ) ) {

                tablesToDrop.add( ccp.tableId );
            }

            for ( Long id : tablesToDrop ) {
                if ( catalog.getTable( id ).entityType != EntityType.MATERIALIZED_VIEW ) {
                    tablesToDrop.add( id );
                }
            }

            // Remove foreign keys
            for ( Long tableId : tablesToDrop ) {
                for ( CatalogForeignKey fk : catalog.getForeignKeys( tableId ) ) {
                    try {
                        catalog.deleteForeignKey( fk.id );
                    } catch ( GenericCatalogException e ) {
                        throw new PolyphenyDbContextException( "Exception while dropping foreign key", e );
                    }
                }
            }
            // Drop tables
            for ( Long tableId : tablesToDrop ) {
                CatalogEntity table = catalog.getTable( tableId );

                // Make sure that there is only one adapter
                if ( table.dataPlacements.size() != 1 ) {
                    throw new RuntimeException( "The data source contains tables with more than one placement. This should not happen!" );
                }

                // Make sure table is of type source
                if ( table.entityType != EntityType.SOURCE ) {
                    throw new RuntimeException( "Trying to drop a table located on a data source which is not of table type SOURCE. This should not happen!" );
                }

                // Delete column placement in catalog
                for ( Long columnId : table.fieldIds ) {
                    if ( catalog.checkIfExistsColumnPlacement( catalogAdapter.id, columnId ) ) {
                        catalog.deleteColumnPlacement( catalogAdapter.id, columnId, false );
                    }
                }

                // Remove primary keys
                try {
                    catalog.deletePrimaryKey( table.id );
                } catch ( GenericCatalogException e ) {
                    throw new PolyphenyDbContextException( "Exception while dropping primary key", e );
                }

                // Delete columns
                for ( Long columnId : table.fieldIds ) {
                    catalog.deleteColumn( columnId );
                }

                // Delete the table
                catalog.deleteTable( table.id );
            }

            // Reset plan cache implementation cache & routing cache
            statement.getQueryProcessor().resetCaches();
        }
        AdapterManager.getInstance().removeAdapter( catalogAdapter.id );
    }


    @Override
    public void alterSchemaOwner( String schemaName, String ownerName, long databaseId ) throws UnknownUserException, UnknownNamespaceException {
        CatalogNamespace catalogNamespace = catalog.getNamespace( databaseId, schemaName );
        CatalogUser catalogUser = catalog.getUser( ownerName );
        catalog.setSchemaOwner( catalogNamespace.id, catalogUser.id );
    }


    @Override
    public void renameSchema( String newName, String oldName, long databaseId ) throws NamespaceAlreadyExistsException, UnknownNamespaceException {
        if ( catalog.checkIfExistsNamespace( databaseId, newName ) ) {
            throw new NamespaceAlreadyExistsException();
        }
        CatalogNamespace catalogNamespace = catalog.getNamespace( databaseId, oldName );
        catalog.renameSchema( catalogNamespace.id, newName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateSchemaName( catalogNamespace, newName );
    }


    @Override
    public void addColumnToSourceTable( CatalogEntity catalogEntity, String columnPhysicalName, String columnLogicalName, String beforeColumnName, String afterColumnName, String defaultValue, Statement statement ) throws ColumnAlreadyExistsException, DdlOnSourceException, ColumnNotExistsException {

        if ( catalog.checkIfExistsColumn( catalogEntity.id, columnLogicalName ) ) {
            throw new ColumnAlreadyExistsException( columnLogicalName, catalogEntity.name );
        }

        CatalogColumn beforeColumn = beforeColumnName == null ? null : getCatalogColumn( catalogEntity.id, beforeColumnName );
        CatalogColumn afterColumn = afterColumnName == null ? null : getCatalogColumn( catalogEntity.id, afterColumnName );

        // Make sure that the table is of table type SOURCE
        if ( catalogEntity.entityType != EntityType.SOURCE ) {
            throw new RuntimeException( "Illegal operation on table of type " + catalogEntity.entityType );
        }

        // Make sure there is only one adapter
        if ( catalog.getColumnPlacement( catalogEntity.fieldIds.get( 0 ) ).size() != 1 ) {
            throw new RuntimeException( "The table has an unexpected number of placements!" );
        }

        int adapterId = catalog.getColumnPlacement( catalogEntity.fieldIds.get( 0 ) ).get( 0 ).adapterId;
        DataSource dataSource = (DataSource) AdapterManager.getInstance().getAdapter( adapterId );

        String physicalTableName = catalog.getPartitionPlacement( adapterId, catalogEntity.partitionProperty.partitionIds.get( 0 ) ).physicalTableName;
        List<ExportedColumn> exportedColumns = dataSource.getExportedColumns().get( physicalTableName );

        // Check if physicalColumnName is valid
        ExportedColumn exportedColumn = null;
        for ( ExportedColumn ec : exportedColumns ) {
            if ( ec.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                exportedColumn = ec;
            }
        }
        if ( exportedColumn == null ) {
            throw new RuntimeException( "Invalid physical column name '" + columnPhysicalName + "'!" );
        }

        // Make sure this physical column has not already been added to this table
        for ( CatalogColumnPlacement ccp : catalog.getColumnPlacementsOnAdapterPerTable( adapterId, catalogEntity.id ) ) {
            if ( ccp.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                throw new RuntimeException( "The physical column '" + columnPhysicalName + "' has already been added to this table!" );
            }
        }

        int position = updateAdjacentPositions( catalogEntity, beforeColumn, afterColumn );

        long columnId = catalog.addColumn(
                columnLogicalName,
                catalogEntity.id,
                position,
                exportedColumn.type,
                exportedColumn.collectionsType,
                exportedColumn.length,
                exportedColumn.scale,
                exportedColumn.dimension,
                exportedColumn.cardinality,
                exportedColumn.nullable,
                Collation.getDefaultCollation()
        );

        // Add default value
        addDefaultValue( defaultValue, columnId );
        CatalogColumn addedColumn = catalog.getField( columnId );

        // Add column placement
        catalog.addColumnPlacement(
                adapterId,
                addedColumn.id,
                PlacementType.STATIC,
                exportedColumn.physicalSchemaName,
                exportedColumn.physicalTableName,
                exportedColumn.physicalColumnName
        );//Not a valid partitionID --> placeholder

        // Set column position
        catalog.updateColumnPlacementPhysicalPosition( adapterId, columnId, exportedColumn.physicalPosition );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private int updateAdjacentPositions( CatalogEntity catalogEntity, CatalogColumn beforeColumn, CatalogColumn afterColumn ) {
        List<CatalogColumn> columns = catalog.getColumns( catalogEntity.id );
        int position = columns.size() + 1;
        if ( beforeColumn != null || afterColumn != null ) {
            if ( beforeColumn != null ) {
                position = beforeColumn.position;
            } else {
                position = afterColumn.position + 1;
            }
            // Update position of the other columns
            for ( int i = columns.size(); i >= position; i-- ) {
                catalog.setColumnPosition( columns.get( i - 1 ).id, i + 1 );
            }
        }
        return position;
    }


    @Override
    public void addColumn( String columnName, CatalogEntity catalogEntity, String beforeColumnName, String afterColumnName, ColumnTypeInformation type, boolean nullable, String defaultValue, Statement statement ) throws NotNullAndDefaultValueException, ColumnAlreadyExistsException, ColumnNotExistsException {
        // Check if the column either allows null values or has a default value defined.
        if ( defaultValue == null && !nullable ) {
            throw new NotNullAndDefaultValueException();
        }

        if ( catalog.checkIfExistsColumn( catalogEntity.id, columnName ) ) {
            throw new ColumnAlreadyExistsException( columnName, catalogEntity.name );
        }
        //
        CatalogColumn beforeColumn = beforeColumnName == null ? null : getCatalogColumn( catalogEntity.id, beforeColumnName );
        CatalogColumn afterColumn = afterColumnName == null ? null : getCatalogColumn( catalogEntity.id, afterColumnName );

        int position = updateAdjacentPositions( catalogEntity, beforeColumn, afterColumn );

        long columnId = catalog.addColumn(
                columnName,
                catalogEntity.id,
                position,
                type.type,
                type.collectionType,
                type.precision,
                type.scale,
                type.dimension,
                type.cardinality,
                nullable,
                Collation.getDefaultCollation()
        );

        // Add default value
        addDefaultValue( defaultValue, columnId );
        CatalogColumn addedColumn = catalog.getField( columnId );

        // Ask router on which stores this column shall be placed
        List<DataStore> stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewColumn( addedColumn );

        // Add column on underlying data stores and insert default value
        for ( DataStore store : stores ) {
            catalog.addColumnPlacement(
                    store.getAdapterId(),
                    addedColumn.id,
                    PlacementType.AUTOMATIC,
                    null, // Will be set later
                    null, // Will be set later
                    null // Will be set later
            );//Not a valid partitionID --> placeholder
        }

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addForeignKey( CatalogEntity catalogEntity, CatalogEntity refTable, List<String> columnNames, List<String> refColumnNames, String constraintName, ForeignKeyOption onUpdate, ForeignKeyOption onDelete ) throws UnknownColumnException, GenericCatalogException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            CatalogColumn catalogColumn = catalog.getField( catalogEntity.id, columnName );
            columnIds.add( catalogColumn.id );
        }
        List<Long> referencesIds = new LinkedList<>();
        for ( String columnName : refColumnNames ) {
            CatalogColumn catalogColumn = catalog.getField( refTable.id, columnName );
            referencesIds.add( catalogColumn.id );
        }
        catalog.addForeignKey( catalogEntity.id, columnIds, refTable.id, referencesIds, constraintName, onUpdate, onDelete );
    }


    @Override
    public void addIndex( CatalogEntity catalogEntity, String indexMethodName, List<String> columnNames, String indexName, boolean isUnique, DataStore location, Statement statement ) throws UnknownColumnException, UnknownIndexMethodException, GenericCatalogException, UnknownTableException, UnknownUserException, UnknownNamespaceException, UnknownKeyException, UnknownDatabaseException, TransactionException, AlterSourceException, IndexExistsException, MissingColumnPlacementException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            CatalogColumn catalogColumn = catalog.getField( catalogEntity.id, columnName );
            columnIds.add( catalogColumn.id );
        }

        IndexType type = IndexType.MANUAL;

        // Make sure that this is a table of type TABLE (and not SOURCE)
        if ( catalogEntity.entityType != EntityType.ENTITY && catalogEntity.entityType != EntityType.MATERIALIZED_VIEW ) {
            throw new RuntimeException( "It is only possible to add an index to a " + catalogEntity.entityType.name() );
        }

        // Check if there is already an index with this name for this table
        if ( catalog.checkIfExistsIndex( catalogEntity.id, indexName ) ) {
            throw new IndexExistsException();
        }

        if ( location == null ) { // Polystore Index
            String method;
            String methodDisplayName;
            if ( indexMethodName != null ) {
                AvailableIndexMethod aim = null;
                for ( AvailableIndexMethod availableIndexMethod : IndexManager.getAvailableIndexMethods() ) {
                    if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                        aim = availableIndexMethod;
                    }
                }
                if ( aim == null ) {
                    throw new UnknownIndexMethodException();
                }
                method = aim.name;
                methodDisplayName = aim.displayName;
            } else {
                method = IndexManager.getDefaultIndexMethod().name;
                methodDisplayName = IndexManager.getDefaultIndexMethod().displayName;
            }

            long indexId = catalog.addIndex(
                    catalogEntity.id,
                    columnIds,
                    isUnique,
                    method,
                    methodDisplayName,
                    0,
                    type,
                    indexName );

            IndexManager.getInstance().addIndex( catalog.getIndex( indexId ), statement );
        } else { // Store Index

            // Check if there if all required columns are present on this store
            for ( long columnId : columnIds ) {
                if ( !catalog.checkIfExistsColumnPlacement( location.getAdapterId(), columnId ) ) {
                    throw new MissingColumnPlacementException( catalog.getField( columnId ).name );
                }
            }

            String method;
            String methodDisplayName;
            if ( indexMethodName != null ) {
                AvailableIndexMethod aim = null;
                for ( AvailableIndexMethod availableIndexMethod : location.getAvailableIndexMethods() ) {
                    if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                        aim = availableIndexMethod;
                    }
                }
                if ( aim == null ) {
                    throw new UnknownIndexMethodException();
                }
                method = aim.name;
                methodDisplayName = aim.displayName;
            } else {
                method = location.getDefaultIndexMethod().name;
                methodDisplayName = location.getDefaultIndexMethod().displayName;
            }

            long indexId = catalog.addIndex(
                    catalogEntity.id,
                    columnIds,
                    isUnique,
                    method,
                    methodDisplayName,
                    location.getAdapterId(),
                    type,
                    indexName );

            location.addIndex( statement.getPrepareContext(), catalog.getIndex( indexId ), catalog.getPartitionsOnDataPlacement( location.getAdapterId(), catalogEntity.id ) );
        }
    }


    @Override
    public void addDataPlacement( CatalogEntity catalogEntity, List<Long> columnIds, List<Integer> partitionGroupIds, List<String> partitionGroupNames, DataStore dataStore, Statement statement ) throws PlacementAlreadyExistsException {
        List<CatalogColumn> addedColumns = new LinkedList<>();

        List<Long> tempPartitionGroupList = new ArrayList<>();

        if ( catalogEntity.dataPlacements.contains( dataStore.getAdapterId() ) ) {
            throw new PlacementAlreadyExistsException();
        } else {
            catalog.addDataPlacement( dataStore.getAdapterId(), catalogEntity.id );
        }

        // Check whether the list is empty (this is a shorthand for a full placement)
        if ( columnIds.size() == 0 ) {
            columnIds = ImmutableList.copyOf( catalogEntity.fieldIds );
        }

        // Select partitions to create on this placement
        boolean isDataPlacementPartitioned = false;
        long tableId = catalogEntity.id;
        // Needed to ensure that column placements on the same store contain all the same partitions
        // Check if this column placement is the first on the data placement
        // If this returns null this means that this is the first placement and partition list can therefore be specified
        List<Long> currentPartList = catalog.getPartitionGroupsOnDataPlacement( dataStore.getAdapterId(), catalogEntity.id );

        isDataPlacementPartitioned = !currentPartList.isEmpty();

        if ( !partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {

            // Abort if a manual partitionList has been specified even though the data placement has already been partitioned
            if ( isDataPlacementPartitioned ) {
                throw new RuntimeException( "WARNING: The Data Placement for table: '" + catalogEntity.name + "' on store: '"
                        + dataStore.getAdapterName() + "' already contains manually specified partitions: " + currentPartList + ". Use 'ALTER TABLE ... MODIFY PARTITIONS...' instead" );
            }

            log.debug( "Table is partitioned and concrete partitionList has been specified " );
            // First convert specified index to correct partitionGroupId
            for ( int partitionGroupId : partitionGroupIds ) {
                // Check if specified partition index is even part of table and if so get corresponding uniquePartId
                try {
                    tempPartitionGroupList.add( catalogEntity.partitionProperty.partitionGroupIds.get( partitionGroupId ) );
                } catch ( IndexOutOfBoundsException e ) {
                    throw new RuntimeException( "Specified Partition-Index: '" + partitionGroupId + "' is not part of table '"
                            + catalogEntity.name + "', has only " + catalogEntity.partitionProperty.numPartitionGroups + " partitions" );
                }
            }
        } else if ( !partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {

            if ( isDataPlacementPartitioned ) {
                throw new RuntimeException( "WARNING: The Data Placement for table: '" + catalogEntity.name + "' on store: '"
                        + dataStore.getAdapterName() + "' already contains manually specified partitions: " + currentPartList + ". Use 'ALTER TABLE ... MODIFY PARTITIONS...' instead" );
            }

            List<CatalogPartitionGroup> catalogPartitionGroups = catalog.getPartitionGroups( tableId );
            for ( String partitionName : partitionGroupNames ) {
                boolean isPartOfTable = false;
                for ( CatalogPartitionGroup catalogPartitionGroup : catalogPartitionGroups ) {
                    if ( partitionName.equals( catalogPartitionGroup.partitionGroupName.toLowerCase() ) ) {
                        tempPartitionGroupList.add( catalogPartitionGroup.id );
                        isPartOfTable = true;
                        break;
                    }
                }
                if ( !isPartOfTable ) {
                    throw new RuntimeException( "Specified Partition-Name: '" + partitionName + "' is not part of table '"
                            + catalogEntity.name + "'. Available partitions: " + String.join( ",", catalog.getPartitionGroupNames( tableId ) ) );

                }
            }
        }
        // Simply Place all partitions on placement since nothing has been specified
        else if ( partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {
            log.debug( "Table is partitioned and concrete partitionList has NOT been specified " );

            if ( isDataPlacementPartitioned ) {
                // If DataPlacement already contains partitions then create new placement with same set of partitions.
                tempPartitionGroupList = currentPartList;
            } else {
                tempPartitionGroupList = catalogEntity.partitionProperty.partitionGroupIds;
            }
        }
        //}

        //all internal partitions placed on this store
        List<Long> partitionIds = new ArrayList<>();

        // Gather all partitions relevant to add depending on the specified partitionGroup
        tempPartitionGroupList.forEach( pg -> catalog.getPartitions( pg ).forEach( p -> partitionIds.add( p.id ) ) );

        // Create column placements
        for ( long cid : columnIds ) {
            catalog.addColumnPlacement(
                    dataStore.getAdapterId(),
                    cid,
                    PlacementType.MANUAL,
                    null,
                    null,
                    null
            );
            addedColumns.add( catalog.getField( cid ) );
        }
        // Check if placement includes primary key columns
        CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogEntity.primaryKey );
        for ( long cid : primaryKey.columnIds ) {
            if ( !columnIds.contains( cid ) ) {
                catalog.addColumnPlacement(
                        dataStore.getAdapterId(),
                        cid,
                        PlacementType.AUTOMATIC,
                        null,
                        null,
                        null
                );
                addedColumns.add( catalog.getField( cid ) );
            }
        }

        // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
        for ( long partitionId : partitionIds ) {
            catalog.addPartitionPlacement(
                    dataStore.getAdapterId(),
                    catalogEntity.id,
                    partitionId,
                    PlacementType.AUTOMATIC,
                    null,
                    null,
                    DataPlacementRole.UPTODATE );
        }

        // Make sure that the stores have created the schema
        PolySchemaBuilder.getInstance().getCurrent();

        // Create table on store
        dataStore.createTable( statement.getPrepareContext(), catalogEntity, catalogEntity.partitionProperty.partitionIds );
        // Copy data to the newly added placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( dataStore.getAdapterId() ), addedColumns, partitionIds );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addPrimaryKey( CatalogEntity catalogEntity, List<String> columnNames, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        checkModelLogic( catalogEntity );

        try {
            CatalogPrimaryKey oldPk = catalog.getPrimaryKey( catalogEntity.primaryKey );

            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                CatalogColumn catalogColumn = catalog.getField( catalogEntity.id, columnName );
                columnIds.add( catalogColumn.id );
            }
            catalog.addPrimaryKey( catalogEntity.id, columnIds );

            // Add new column placements
            long pkColumnId = oldPk.columnIds.get( 0 ); // It is sufficient to check for one because all get replicated on all stores
            List<CatalogColumnPlacement> oldPkPlacements = catalog.getColumnPlacement( pkColumnId );
            for ( CatalogColumnPlacement ccp : oldPkPlacements ) {
                for ( long columnId : columnIds ) {
                    if ( !catalog.checkIfExistsColumnPlacement( ccp.adapterId, columnId ) ) {
                        catalog.addColumnPlacement(
                                ccp.adapterId,
                                columnId,
                                PlacementType.AUTOMATIC,
                                null, // Will be set later
                                null, // Will be set later
                                null // Will be set later
                        );
                        AdapterManager.getInstance().getStore( ccp.adapterId ).addColumn(
                                statement.getPrepareContext(),
                                catalog.getTable( ccp.tableId ),
                                catalog.getField( columnId ) );
                    }
                }
            }
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void addUniqueConstraint( CatalogEntity catalogEntity, List<String> columnNames, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        checkModelLogic( catalogEntity, null );

        try {
            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                CatalogColumn catalogColumn = catalog.getField( catalogEntity.id, columnName );
                columnIds.add( catalogColumn.id );
            }
            catalog.addUniqueConstraint( catalogEntity.id, constraintName, columnIds );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropColumn( CatalogEntity catalogEntity, String columnName, Statement statement ) throws ColumnNotExistsException {
        if ( catalogEntity.fieldIds.size() < 2 ) {
            throw new RuntimeException( "Cannot drop sole column of table " + catalogEntity.name );
        }

        // check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        //check if views are dependent from this view
        checkViewDependencies( catalogEntity );

        CatalogColumn column = getCatalogColumn( catalogEntity.id, columnName );

        // Check if column is part of a key
        for ( CatalogKey key : catalog.getTableKeys( catalogEntity.id ) ) {
            if ( key.columnIds.contains( column.id ) ) {
                if ( catalog.isPrimaryKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the primary key." );
                } else if ( catalog.isIndex( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the index with the name: '" + catalog.getIndexes( key ).get( 0 ).name + "'." );
                } else if ( catalog.isForeignKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the foreign key with the name: '" + catalog.getForeignKeys( key ).get( 0 ).name + "'." );
                } else if ( catalog.isConstraint( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the constraint with the name: '" + catalog.getConstraints( key ).get( 0 ).name + "'." );
                }
                throw new PolyphenyDbException( "Ok, strange... Something is going wrong here!" );
            }
        }

        // Delete column from underlying data stores
        for ( CatalogColumnPlacement dp : catalog.getColumnPlacementsByColumn( column.id ) ) {
            if ( catalogEntity.entityType == EntityType.ENTITY ) {
                AdapterManager.getInstance().getStore( dp.adapterId ).dropColumn( statement.getPrepareContext(), dp );
            }
            catalog.deleteColumnPlacement( dp.adapterId, dp.columnId, true );
        }

        // Delete from catalog
        List<CatalogColumn> columns = catalog.getColumns( catalogEntity.id );
        catalog.deleteColumn( column.id );
        if ( column.position != columns.size() ) {
            // Update position of the other columns
            for ( int i = column.position; i < columns.size(); i++ ) {
                catalog.setColumnPosition( columns.get( i ).id, i );
            }
        }

        // Monitor dropColumn for statistics
        prepareMonitoring( statement, Kind.DROP_COLUMN, catalogEntity, column );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private void checkModelLogic( CatalogEntity catalogEntity ) {
        if ( catalogEntity.getNamespaceType() == NamespaceType.DOCUMENT ) {
            throw new RuntimeException( "Modification operation is not allowed by schema type DOCUMENT" );
        }
    }


    private void checkModelLogic( CatalogEntity catalogEntity, String columnName ) {
        if ( catalogEntity.getNamespaceType() == NamespaceType.DOCUMENT
                && (columnName.equals( "_data" ) || columnName.equals( "_id" )) ) {
            throw new RuntimeException( "Modification operation is not allowed by schema type DOCUMENT" );
        }
    }


    @Override
    public void dropConstraint( CatalogEntity catalogEntity, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        try {
            CatalogConstraint constraint = catalog.getConstraint( catalogEntity.id, constraintName );
            catalog.deleteConstraint( constraint.id );
        } catch ( GenericCatalogException | UnknownConstraintException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropForeignKey( CatalogEntity catalogEntity, String foreignKeyName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        try {
            CatalogForeignKey foreignKey = catalog.getForeignKey( catalogEntity.id, foreignKeyName );
            catalog.deleteForeignKey( foreignKey.id );
        } catch ( GenericCatalogException | UnknownForeignKeyException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropIndex( CatalogEntity catalogEntity, String indexName, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        try {
            CatalogIndex index = catalog.getIndex( catalogEntity.id, indexName );

            if ( index.location == 0 ) {
                IndexManager.getInstance().deleteIndex( index );
            } else {
                DataStore storeInstance = AdapterManager.getInstance().getStore( index.location );
                storeInstance.dropIndex( statement.getPrepareContext(), index, catalog.getPartitionsOnDataPlacement( index.location, catalogEntity.id ) );
            }

            catalog.deleteIndex( index.id );
        } catch ( UnknownIndexException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropDataPlacement( CatalogEntity catalogEntity, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, LastPlacementException {

        // Check whether this placement exists
        if ( !catalogEntity.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        CatalogDataPlacement dataPlacement = catalog.getDataPlacement( storeInstance.getAdapterId(), catalogEntity.id );
        if ( !catalog.validateDataPlacementsConstraints( catalogEntity.id, storeInstance.getAdapterId(),
                dataPlacement.columnPlacementsOnAdapter, dataPlacement.getAllPartitionIds() ) ) {

            throw new LastPlacementException();
        }

        // Drop all indexes on this store
        for ( CatalogIndex index : catalog.getIndexes( catalogEntity.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() ) {
                if ( index.location == 0 ) {
                    // Delete polystore index
                    IndexManager.getInstance().deleteIndex( index );
                } else {
                    // Delete index on store
                    AdapterManager.getInstance().getStore( index.location ).dropIndex(
                            statement.getPrepareContext(),
                            index,
                            catalog.getPartitionsOnDataPlacement( index.location, catalogEntity.id ) );
                }
                // Delete index in catalog
                catalog.deleteIndex( index.id );
            }
        }
        // Physically delete the data from the store
        storeInstance.dropTable( statement.getPrepareContext(), catalogEntity, catalog.getPartitionsOnDataPlacement( storeInstance.getAdapterId(), catalogEntity.id ) );

        // Remove physical stores afterwards
        catalog.removeDataPlacement( storeInstance.getAdapterId(), catalogEntity.id );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropPrimaryKey( CatalogEntity catalogEntity ) throws DdlOnSourceException {
        try {
            // Make sure that this is a table of type TABLE (and not SOURCE)
            checkIfDdlPossible( catalogEntity.entityType );
            catalog.deletePrimaryKey( catalogEntity.id );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void setColumnType( CatalogEntity catalogEntity, String columnName, ColumnTypeInformation type, Statement statement ) throws DdlOnSourceException, ColumnNotExistsException, GenericCatalogException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        // check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        catalog.setColumnType(
                catalogColumn.id,
                type.type,
                type.collectionType,
                type.precision,
                type.scale,
                type.dimension,
                type.cardinality );
        for ( CatalogColumnPlacement placement : catalog.getColumnPlacement( catalogColumn.id ) ) {
            AdapterManager.getInstance().getStore( placement.adapterId ).updateColumnType(
                    statement.getPrepareContext(),
                    placement,
                    catalog.getField( catalogColumn.id ),
                    catalogColumn.type );
        }

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnNullable( CatalogEntity catalogEntity, String columnName, boolean nullable, Statement statement ) throws ColumnNotExistsException, DdlOnSourceException, GenericCatalogException {
        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        // Check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        catalog.setNullable( catalogColumn.id, nullable );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnPosition( CatalogEntity catalogEntity, String columnName, String beforeColumnName, String afterColumnName, Statement statement ) throws ColumnNotExistsException {
        // Check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        int targetPosition;
        CatalogColumn refColumn;
        if ( beforeColumnName != null ) {
            refColumn = getCatalogColumn( catalogEntity.id, beforeColumnName );
            targetPosition = refColumn.position;
        } else {
            refColumn = getCatalogColumn( catalogEntity.id, afterColumnName );
            targetPosition = refColumn.position + 1;
        }
        if ( catalogColumn.id == refColumn.id ) {
            throw new RuntimeException( "Same column!" );
        }
        List<CatalogColumn> columns = catalog.getColumns( catalogEntity.id );
        if ( targetPosition < catalogColumn.position ) {  // Walk from last column to first column
            for ( int i = columns.size(); i >= 1; i-- ) {
                if ( i < catalogColumn.position && i >= targetPosition ) {
                    catalog.setColumnPosition( columns.get( i - 1 ).id, i + 1 );
                } else if ( i == catalogColumn.position ) {
                    catalog.setColumnPosition( catalogColumn.id, columns.size() + 1 );
                }
                if ( i == targetPosition ) {
                    catalog.setColumnPosition( catalogColumn.id, targetPosition );
                }
            }
        } else if ( targetPosition > catalogColumn.position ) { // Walk from first column to last column
            targetPosition--;
            for ( int i = 1; i <= columns.size(); i++ ) {
                if ( i > catalogColumn.position && i <= targetPosition ) {
                    catalog.setColumnPosition( columns.get( i - 1 ).id, i - 1 );
                } else if ( i == catalogColumn.position ) {
                    catalog.setColumnPosition( catalogColumn.id, columns.size() + 1 );
                }
                if ( i == targetPosition ) {
                    catalog.setColumnPosition( catalogColumn.id, targetPosition );
                }
            }
        }
        // Do nothing

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnCollation( CatalogEntity catalogEntity, String columnName, Collation collation, Statement statement ) throws ColumnNotExistsException, DdlOnSourceException {
        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // Check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogEntity.entityType );

        catalog.setCollation( catalogColumn.id, collation );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setDefaultValue( CatalogEntity catalogEntity, String columnName, String defaultValue, Statement statement ) throws ColumnNotExistsException {
        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // Check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        addDefaultValue( defaultValue, catalogColumn.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropDefaultValue( CatalogEntity catalogEntity, String columnName, Statement statement ) throws ColumnNotExistsException {
        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // check if model permits operation
        checkModelLogic( catalogEntity, columnName );

        catalog.deleteDefaultValue( catalogColumn.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void modifyDataPlacement( CatalogEntity catalogEntity, List<Long> columnIds, List<Integer> partitionGroupIds, List<String> partitionGroupNames, DataStore storeInstance, Statement statement )
            throws PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException {

        // Check whether this placement already exists
        if ( !catalogEntity.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        // Check if views are dependent from this view
        checkViewDependencies( catalogEntity );

        List<Long> columnsToRemove = new ArrayList<>();

        // Checks before physically removing of placement that the partition distribution is still valid and sufficient
        // Identifies which columns need to be removed
        for ( CatalogColumnPlacement placement : catalog.getColumnPlacementsOnAdapterPerTable( storeInstance.getAdapterId(), catalogEntity.id ) ) {
            if ( !columnIds.contains( placement.columnId ) ) {
                // Check whether there are any indexes located on the store requiring this column
                for ( CatalogIndex index : catalog.getIndexes( catalogEntity.id, false ) ) {
                    if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( placement.columnId ) ) {
                        throw new IndexPreventsRemovalException( index.name, catalog.getField( placement.columnId ).name );
                    }
                }
                // Check whether the column is a primary key column
                CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogEntity.primaryKey );
                if ( primaryKey.columnIds.contains( placement.columnId ) ) {
                    // Check if the placement type is manual. If so, change to automatic
                    if ( placement.placementType == PlacementType.MANUAL ) {
                        // Make placement manual
                        catalog.updateColumnPlacementType(
                                storeInstance.getAdapterId(),
                                placement.columnId,
                                PlacementType.AUTOMATIC );
                    }
                } else {
                    // It is not a primary key. Remove the column
                    columnsToRemove.add( placement.columnId );
                }
            }
        }

        if ( !catalog.validateDataPlacementsConstraints( catalogEntity.id, storeInstance.getAdapterId(), columnsToRemove, new ArrayList<>() ) ) {
            throw new LastPlacementException();
        }

        boolean adjustPartitions = true;
        // Remove columns physically
        for ( long columnId : columnsToRemove ) {
            // Drop Column on store
            storeInstance.dropColumn( statement.getPrepareContext(), catalog.getColumnPlacement( storeInstance.getAdapterId(), columnId ) );
            // Drop column placement
            catalog.deleteColumnPlacement( storeInstance.getAdapterId(), columnId, true );
        }

        List<Long> tempPartitionGroupList = new ArrayList<>();

        // Select partitions to create on this placement
        if ( catalogEntity.partitionProperty.isPartitioned ) {
            long tableId = catalogEntity.id;
            // If index partitions are specified
            if ( !partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {
                // First convert specified index to correct partitionGroupId
                for ( int partitionGroupId : partitionGroupIds ) {
                    // Check if specified partition index is even part of table and if so get corresponding uniquePartId
                    try {
                        int index = catalogEntity.partitionProperty.partitionGroupIds.indexOf( partitionGroupId );
                        tempPartitionGroupList.add( catalogEntity.partitionProperty.partitionGroupIds.get( index ) );
                    } catch ( IndexOutOfBoundsException e ) {
                        throw new RuntimeException( "Specified Partition-Index: '" + partitionGroupId + "' is not part of table '"
                                + catalogEntity.name + "', has only " + catalogEntity.partitionProperty.partitionGroupIds.size() + " partitions" );
                    }
                }
            }
            // If name partitions are specified
            else if ( !partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {
                List<CatalogPartitionGroup> catalogPartitionGroups = catalog.getPartitionGroups( tableId );
                for ( String partitionName : partitionGroupNames ) {
                    boolean isPartOfTable = false;
                    for ( CatalogPartitionGroup catalogPartitionGroup : catalogPartitionGroups ) {
                        if ( partitionName.equals( catalogPartitionGroup.partitionGroupName.toLowerCase() ) ) {
                            tempPartitionGroupList.add( catalogPartitionGroup.id );
                            isPartOfTable = true;
                            break;
                        }
                    }
                    if ( !isPartOfTable ) {
                        throw new RuntimeException( "Specified partition name: '" + partitionName + "' is not part of table '"
                                + catalogEntity.name + "'. Available partitions: " + String.join( ",", catalog.getPartitionGroupNames( tableId ) ) );
                    }
                }
            } else if ( partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {
                // If nothing has been explicitly specified keep current placement of partitions.
                // Since it's impossible to have a placement without any partitions anyway
                log.debug( "Table is partitioned and concrete partitionList has NOT been specified " );
                tempPartitionGroupList = catalogEntity.partitionProperty.partitionGroupIds;
            }
        } else {
            tempPartitionGroupList.add( catalogEntity.partitionProperty.partitionGroupIds.get( 0 ) );
        }

        // All internal partitions placed on this store
        List<Long> intendedPartitionIds = new ArrayList<>();

        // Gather all partitions relevant to add depending on the specified partitionGroup
        tempPartitionGroupList.forEach( pg -> catalog.getPartitions( pg ).forEach( p -> intendedPartitionIds.add( p.id ) ) );

        // Which columns to add
        List<CatalogColumn> addedColumns = new LinkedList<>();

        for ( long cid : columnIds ) {
            if ( catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), cid ) ) {
                CatalogColumnPlacement placement = catalog.getColumnPlacement( storeInstance.getAdapterId(), cid );
                if ( placement.placementType == PlacementType.AUTOMATIC ) {
                    // Make placement manual
                    catalog.updateColumnPlacementType( storeInstance.getAdapterId(), cid, PlacementType.MANUAL );
                }
            } else {
                // Create column placement
                catalog.addColumnPlacement(
                        storeInstance.getAdapterId(),
                        cid,
                        PlacementType.MANUAL,
                        null,
                        null,
                        null
                );
                // Add column on store
                storeInstance.addColumn( statement.getPrepareContext(), catalogEntity, catalog.getField( cid ) );
                // Add to list of columns for which we need to copy data
                addedColumns.add( catalog.getField( cid ) );
            }
        }

        CatalogDataPlacement dataPlacement = catalog.getDataPlacement( storeInstance.getAdapterId(), catalogEntity.id );
        List<Long> removedPartitionIdsFromDataPlacement = new ArrayList<>();
        // Removed Partition Ids
        for ( long partitionId : dataPlacement.getAllPartitionIds() ) {
            if ( !intendedPartitionIds.contains( partitionId ) ) {
                removedPartitionIdsFromDataPlacement.add( partitionId );
            }
        }

        List<Long> newPartitionIdsOnDataPlacement = new ArrayList<>();
        // Added Partition Ids
        for ( long partitionId : intendedPartitionIds ) {
            if ( !dataPlacement.getAllPartitionIds().contains( partitionId ) ) {
                newPartitionIdsOnDataPlacement.add( partitionId );
            }
        }

        if ( removedPartitionIdsFromDataPlacement.size() > 0 ) {
            storeInstance.dropTable( statement.getPrepareContext(), catalogEntity, removedPartitionIdsFromDataPlacement );
        }

        if ( newPartitionIdsOnDataPlacement.size() > 0 ) {

            newPartitionIdsOnDataPlacement.forEach( partitionId -> catalog.addPartitionPlacement(
                    storeInstance.getAdapterId(),
                    catalogEntity.id,
                    partitionId,
                    PlacementType.MANUAL,
                    null,
                    null,
                    DataPlacementRole.UPTODATE )
            );

            storeInstance.createTable( statement.getPrepareContext(), catalogEntity, newPartitionIdsOnDataPlacement );
        }

        // Copy the data to the newly added column placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        if ( addedColumns.size() > 0 ) {
            dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( storeInstance.getAdapterId() ), addedColumns, intendedPartitionIds );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void modifyPartitionPlacement( CatalogEntity catalogEntity, List<Long> partitionGroupIds, DataStore storeInstance, Statement statement ) throws LastPlacementException {
        int storeId = storeInstance.getAdapterId();
        List<Long> newPartitions = new ArrayList<>();
        List<Long> removedPartitions = new ArrayList<>();

        List<Long> currentPartitionGroupsOnStore = catalog.getPartitionGroupsOnDataPlacement( storeId, catalogEntity.id );

        // Get PartitionGroups that have been removed
        for ( long partitionGroupId : currentPartitionGroupsOnStore ) {
            if ( !partitionGroupIds.contains( partitionGroupId ) ) {
                catalog.getPartitions( partitionGroupId ).forEach( p -> removedPartitions.add( p.id ) );
            }
        }

        if ( !catalog.validateDataPlacementsConstraints( catalogEntity.id, storeInstance.getAdapterId(), new ArrayList<>(), removedPartitions ) ) {
            throw new LastPlacementException();
        }

        // Get PartitionGroups that have been newly added
        for ( Long partitionGroupId : partitionGroupIds ) {
            if ( !currentPartitionGroupsOnStore.contains( partitionGroupId ) ) {
                catalog.getPartitions( partitionGroupId ).forEach( p -> newPartitions.add( p.id ) );
            }
        }

        // Copy the data to the newly added column placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        if ( newPartitions.size() > 0 ) {
            // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
            for ( long partitionId : newPartitions ) {
                catalog.addPartitionPlacement(
                        storeInstance.getAdapterId(),
                        catalogEntity.id,
                        partitionId,
                        PlacementType.AUTOMATIC,
                        null,
                        null,
                        DataPlacementRole.UPTODATE );
            }

            storeInstance.createTable( statement.getPrepareContext(), catalogEntity, newPartitions );

            // Get only columns that are actually on that store
            List<CatalogColumn> necessaryColumns = new LinkedList<>();
            catalog.getColumnPlacementsOnAdapterPerTable( storeInstance.getAdapterId(), catalogEntity.id ).forEach( cp -> necessaryColumns.add( catalog.getField( cp.columnId ) ) );
            dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( storeId ), necessaryColumns, newPartitions );

            // Add indexes on this new Partition Placement if there is already an index
            for ( CatalogIndex currentIndex : catalog.getIndexes( catalogEntity.id, false ) ) {
                if ( currentIndex.location == storeId ) {
                    storeInstance.addIndex( statement.getPrepareContext(), currentIndex, newPartitions );
                }
            }
        }

        if ( removedPartitions.size() > 0 ) {
            storeInstance.dropTable( statement.getPrepareContext(), catalogEntity, removedPartitions );

            //  Indexes on this new Partition Placement if there is already an index
            for ( CatalogIndex currentIndex : catalog.getIndexes( catalogEntity.id, false ) ) {
                if ( currentIndex.location == storeId ) {
                    storeInstance.dropIndex( statement.getPrepareContext(), currentIndex, removedPartitions );
                }
            }
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addColumnPlacement( CatalogEntity catalogEntity, String columnName, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, PlacementAlreadyExistsException, ColumnNotExistsException {
        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );
        }
        // Check whether this placement already exists
        if ( !catalogEntity.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // Make sure that this store does not contain a placement of this column
        if ( catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) ) {
            CatalogColumnPlacement placement = catalog.getColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id );
            if ( placement.placementType == PlacementType.AUTOMATIC ) {
                // Make placement manual
                catalog.updateColumnPlacementType(
                        storeInstance.getAdapterId(),
                        catalogColumn.id,
                        PlacementType.MANUAL );
            } else {
                throw new PlacementAlreadyExistsException();
            }
        } else {
            // Create column placement
            catalog.addColumnPlacement(
                    storeInstance.getAdapterId(),
                    catalogColumn.id,
                    PlacementType.MANUAL,
                    null,
                    null,
                    null
            );
            // Add column on store
            storeInstance.addColumn( statement.getPrepareContext(), catalogEntity, catalogColumn );
            // Copy the data to the newly added column placements
            DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
            dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( storeInstance.getAdapterId() ),
                    ImmutableList.of( catalogColumn ), catalog.getPartitionsOnDataPlacement( storeInstance.getAdapterId(), catalogEntity.id ) );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropColumnPlacement( CatalogEntity catalogEntity, String columnName, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException, PlacementIsPrimaryException, ColumnNotExistsException {
        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );
        }
        // Check whether this placement already exists
        if ( !catalogEntity.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        // Check whether this store actually contains a placement of this column
        if ( !catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) ) {
            throw new PlacementNotExistsException();
        }
        // Check whether there are any indexes located on the store requiring this column
        for ( CatalogIndex index : catalog.getIndexes( catalogEntity.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( catalogColumn.id ) ) {
                throw new IndexPreventsRemovalException( index.name, columnName );
            }
        }

        if ( !catalog.validateDataPlacementsConstraints( catalogColumn.tableId, storeInstance.getAdapterId(), Arrays.asList( catalogColumn.id ), new ArrayList<>() ) ) {
            throw new LastPlacementException();
        }

        // Check whether the column to drop is a primary key
        CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogEntity.primaryKey );
        if ( primaryKey.columnIds.contains( catalogColumn.id ) ) {
            throw new PlacementIsPrimaryException();
        }
        // Drop Column on store
        storeInstance.dropColumn( statement.getPrepareContext(), catalog.getColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) );
        // Drop column placement
        catalog.deleteColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id, false );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void alterTableOwner( CatalogEntity catalogEntity, String newOwnerName ) throws UnknownUserException {
        CatalogUser catalogUser = catalog.getUser( newOwnerName );
        catalog.setTableOwner( catalogEntity.id, catalogUser.id );
    }


    @Override
    public void renameTable( CatalogEntity catalogEntity, String newTableName, Statement statement ) throws EntityAlreadyExistsException {
        if ( catalog.checkIfExistsEntity( catalogEntity.namespaceId, newTableName ) ) {
            throw new EntityAlreadyExistsException();
        }
        // Check if views are dependent from this view
        checkViewDependencies( catalogEntity );

        catalog.renameTable( catalogEntity.id, newTableName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateTableName( catalogEntity, newTableName );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void renameColumn( CatalogEntity catalogEntity, String columnName, String newColumnName, Statement statement ) throws ColumnAlreadyExistsException, ColumnNotExistsException {
        CatalogColumn catalogColumn = getCatalogColumn( catalogEntity.id, columnName );

        if ( catalog.checkIfExistsColumn( catalogColumn.tableId, newColumnName ) ) {
            throw new ColumnAlreadyExistsException( newColumnName, catalogColumn.getTableName() );
        }
        // Check if views are dependent from this view
        checkViewDependencies( catalogEntity );

        catalog.renameColumn( catalogColumn.id, newColumnName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateColumnName( catalogColumn, newColumnName );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void createView( String viewName, long schemaId, AlgNode algNode, AlgCollation algCollation, boolean replace, Statement statement, PlacementType placementType, List<String> projectedColumns, String query, QueryLanguage language ) throws EntityAlreadyExistsException {
        if ( catalog.checkIfExistsEntity( schemaId, viewName ) ) {
            if ( replace ) {
                try {
                    dropView( catalog.getTable( schemaId, viewName ), statement );
                } catch ( UnknownTableException | DdlOnSourceException e ) {
                    throw new RuntimeException( "Unable tp drop the existing View with this name." );
                }
            } else {
                throw new EntityAlreadyExistsException();
            }
        }

        AlgDataType fieldList = algNode.getRowType();

        List<FieldInformation> columns = getColumnInformation( projectedColumns, fieldList );

        Map<Long, List<Long>> underlyingTables = new HashMap<>();

        findUnderlyingTablesOfView( algNode, underlyingTables, fieldList );

        // add check if underlying table is of model document -> mql, relational -> sql
        underlyingTables.keySet().forEach( tableId -> checkModelLangCompatibility( language, tableId ) );

        long tableId = catalog.addView(
                viewName,
                schemaId,
                statement.getPrepareContext().getCurrentUserId(),
                EntityType.VIEW,
                false,
                algNode,
                algCollation,
                underlyingTables,
                fieldList,
                query,
                language
        );

        for ( FieldInformation column : columns ) {
            catalog.addColumn(
                    column.name,
                    tableId,
                    column.position,
                    column.typeInformation.type,
                    column.typeInformation.collectionType,
                    column.typeInformation.precision,
                    column.typeInformation.scale,
                    column.typeInformation.dimension,
                    column.typeInformation.cardinality,
                    column.typeInformation.nullable,
                    column.collation );
        }
    }


    @Override
    public void createMaterializedView( String viewName, long schemaId, AlgRoot algRoot, boolean replace, Statement statement, List<DataStore> stores, PlacementType placementType, List<String> projectedColumns, MaterializedCriteria materializedCriteria, String query, QueryLanguage language, boolean ifNotExists, boolean ordered ) throws EntityAlreadyExistsException, GenericCatalogException {
        // Check if there is already a table with this name
        if ( catalog.checkIfExistsEntity( schemaId, viewName ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a table with this name because "IF NOT EXISTS" was specified
                return;
            } else {
                throw new EntityAlreadyExistsException();
            }
        }

        if ( stores == null ) {
            // Ask router on which store(s) the table should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewTable();
        }

        AlgDataType fieldList = algRoot.alg.getRowType();

        Map<Long, List<Long>> underlyingTables = new HashMap<>();
        Map<Long, List<Long>> underlying = findUnderlyingTablesOfView( algRoot.alg, underlyingTables, fieldList );

        // add check if underlying table is of model document -> mql, relational -> sql
        underlying.keySet().forEach( tableId -> checkModelLangCompatibility( language, tableId ) );

        if ( materializedCriteria.getCriteriaType() == CriteriaType.UPDATE ) {
            List<EntityType> entityTypes = new ArrayList<>();
            underlying.keySet().forEach( t -> entityTypes.add( catalog.getTable( t ).entityType ) );
            if ( !(entityTypes.contains( EntityType.ENTITY )) ) {
                throw new GenericCatalogException( "Not possible to use Materialized View with Update Freshness if underlying table does not include a modifiable table." );
            }
        }

        long tableId = catalog.addMaterializedView(
                viewName,
                schemaId,
                statement.getPrepareContext().getCurrentUserId(),
                EntityType.MATERIALIZED_VIEW,
                false,
                algRoot.alg,
                algRoot.collation,
                underlying,
                fieldList,
                materializedCriteria,
                query,
                language,
                ordered
        );

        // Creates a list with all columns, tableId is needed to create the primary key
        List<FieldInformation> columns = getColumnInformation( projectedColumns, fieldList, true, tableId );
        Map<Integer, List<CatalogColumn>> addedColumns = new HashMap<>();

        List<Long> columnIds = new ArrayList<>();

        for ( FieldInformation column : columns ) {
            long columnId = catalog.addColumn(
                    column.name,
                    tableId,
                    column.position,
                    column.typeInformation.type,
                    column.typeInformation.collectionType,
                    column.typeInformation.precision,
                    column.typeInformation.scale,
                    column.typeInformation.dimension,
                    column.typeInformation.cardinality,
                    column.typeInformation.nullable,
                    column.collation );

            // Created primary key is added to list
            if ( column.name.startsWith( "_matid_" ) ) {
                columnIds.add( columnId );
            }

            for ( DataStore s : stores ) {
                int adapterId = s.getAdapterId();
                catalog.addColumnPlacement(
                        s.getAdapterId(),
                        columnId,
                        placementType,
                        null,
                        null,
                        null
                );

                List<CatalogColumn> catalogColumns;
                if ( addedColumns.containsKey( adapterId ) ) {
                    catalogColumns = addedColumns.get( adapterId );
                } else {
                    catalogColumns = new ArrayList<>();
                }
                catalogColumns.add( catalog.getField( columnId ) );
                addedColumns.put( adapterId, catalogColumns );
            }

        }
        // Sets previously created primary key
        catalog.addPrimaryKey( tableId, columnIds );

        CatalogMaterializedView catalogMaterializedView = (CatalogMaterializedView) catalog.getTable( tableId );
        PolySchemaBuilder.getInstance().getCurrent();

        for ( DataStore store : stores ) {
            catalog.addPartitionPlacement(
                    store.getAdapterId(),
                    tableId,
                    catalogMaterializedView.partitionProperty.partitionIds.get( 0 ),
                    PlacementType.AUTOMATIC,
                    null,
                    null,
                    DataPlacementRole.UPTODATE );

            store.createTable( statement.getPrepareContext(), catalogMaterializedView, catalogMaterializedView.partitionProperty.partitionIds );
        }

        // Selected data from tables is added into the newly crated materialized view
        MaterializedViewManager materializedManager = MaterializedViewManager.getInstance();
        materializedManager.addData( statement.getTransaction(), stores, addedColumns, algRoot, catalogMaterializedView );
    }


    private void checkModelLangCompatibility( QueryLanguage language, Long tableId ) {
        CatalogEntity catalogEntity = catalog.getTable( tableId );
        if ( catalogEntity.getNamespaceType() != language.getNamespaceType() ) {
            throw new RuntimeException(
                    String.format(
                            "The used language cannot execute schema changing queries on this entity with the data model %s.",
                            catalogEntity.getNamespaceType() ) );
        }
    }


    @Override
    public void refreshView( Statement statement, Long materializedId ) {
        MaterializedViewManager materializedManager = MaterializedViewManager.getInstance();
        materializedManager.updateData( statement.getTransaction(), materializedId );
        materializedManager.updateMaterializedTime( materializedId );
    }


    @Override
    public long createGraphDatabase( long databaseId, String graphName, boolean modifiable, @Nullable List<DataStore> stores, boolean ifNotExists, boolean replace, Statement statement ) {
        assert !replace : "Graphs cannot be replaced yet.";

        if ( stores == null ) {
            // Ask router on which store(s) the graph should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewTable();
        }

        // add general graph
        long graphId = catalog.addGraphDatabase( databaseId, graphName, stores, modifiable, ifNotExists, replace );

        try {
            catalog.addGraphLogistics( graphId, stores );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException();
        }

        CatalogGraphDatabase graph = catalog.getGraph( graphId );
        PolySchemaBuilder.getInstance().getCurrent();

        for ( DataStore store : stores ) {
            catalog.addGraphPlacement( store.getAdapterId(), graphId );

            afterGraphLogistics( store, graphId );

            store.createGraph( statement.getPrepareContext(), graph );
        }

        return graphId;

    }


    private void afterGraphLogistics( DataStore store, long graphId ) {
        CatalogGraphMapping mapping = catalog.getGraphMapping( graphId );
        CatalogEntity nodes = catalog.getTable( mapping.nodesId );
        CatalogEntity nodeProperty = catalog.getTable( mapping.nodesPropertyId );
        CatalogEntity edges = catalog.getTable( mapping.edgesId );
        CatalogEntity edgeProperty = catalog.getTable( mapping.edgesPropertyId );

        catalog.addPartitionPlacement(
                store.getAdapterId(),
                nodes.id,
                nodes.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.addPartitionPlacement(
                store.getAdapterId(),
                nodeProperty.id,
                nodeProperty.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.addPartitionPlacement(
                store.getAdapterId(),
                edges.id,
                edges.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.addPartitionPlacement(
                store.getAdapterId(),
                edgeProperty.id,
                edgeProperty.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );


    }


    @Override
    public void addGraphAlias( long graphId, String alias, boolean ifNotExists ) {
        catalog.addGraphAlias( graphId, alias, ifNotExists );
    }


    @Override
    public void removeGraphAlias( long graphId, String alias, boolean ifNotExists ) {
        catalog.removeGraphAlias( alias, ifNotExists );
    }


    @Override
    public void replaceGraphAlias( long graphId, String oldAlias, String alias ) {
        catalog.removeGraphAlias( oldAlias, true );
        catalog.addGraphAlias( graphId, alias, true );
    }


    @Override
    public void removeGraphDatabase( long graphId, boolean ifExists, Statement statement ) {
        CatalogGraphDatabase graph = catalog.getGraph( graphId );

        if ( graph == null ) {
            if ( !ifExists ) {
                throw new UnknownGraphException( graphId );
            }
            return;
        }

        for ( int adapterId : graph.placements ) {
            CatalogGraphPlacement placement = Catalog.getInstance().getGraphPlacement( graphId, adapterId );
            AdapterManager.getInstance().getStore( adapterId ).dropGraph( statement.getPrepareContext(), placement );
        }

        catalog.deleteGraph( graphId );
    }


    private List<FieldInformation> getColumnInformation( List<String> projectedColumns, AlgDataType fieldList ) {
        return getColumnInformation( projectedColumns, fieldList, false, 0 );
    }


    private List<FieldInformation> getColumnInformation( List<String> projectedColumns, AlgDataType fieldList, boolean addPrimary, long tableId ) {
        List<FieldInformation> columns = new ArrayList<>();

        int position = 1;
        for ( AlgDataTypeField alg : fieldList.getFieldList() ) {
            AlgDataType type = alg.getValue();
            if ( alg.getType().getPolyType() == PolyType.ARRAY ) {
                type = ((ArrayType) alg.getValue()).getComponentType();
            }
            String colName = alg.getName();
            if ( projectedColumns != null ) {
                colName = projectedColumns.get( position - 1 );
            }

            columns.add( new FieldInformation(
                    colName.toLowerCase().replaceAll( "[^A-Za-z0-9]", "_" ),
                    new ColumnTypeInformation(
                            type.getPolyType(),
                            alg.getType().getPolyType(),
                            type.getRawPrecision(),
                            type.getScale(),
                            alg.getValue().getPolyType() == PolyType.ARRAY ? (int) ((ArrayType) alg.getValue()).getDimension() : -1,
                            alg.getValue().getPolyType() == PolyType.ARRAY ? (int) ((ArrayType) alg.getValue()).getCardinality() : -1,
                            alg.getValue().isNullable() ),
                    Collation.getDefaultCollation(),
                    null,
                    position ) );
            position++;

        }

        if ( addPrimary ) {
            String primaryName = "_matid_" + tableId;
            columns.add( new FieldInformation(
                    primaryName,
                    new ColumnTypeInformation(
                            PolyType.INTEGER,
                            PolyType.INTEGER,
                            -1,
                            -1,
                            -1,
                            -1,
                            false ),
                    Collation.getDefaultCollation(),
                    null,
                    position ) );
        }

        return columns;
    }


    private Map<Long, List<Long>> findUnderlyingTablesOfView( AlgNode algNode, Map<Long, List<Long>> underlyingTables, AlgDataType fieldList ) {
        if ( algNode instanceof LogicalScan ) {
            List<Long> underlyingColumns = getUnderlyingColumns( algNode, fieldList );
            underlyingTables.put( algNode.getTable().getTable().getTableId(), underlyingColumns );
        } else if ( algNode instanceof LogicalViewScan ) {
            List<Long> underlyingColumns = getUnderlyingColumns( algNode, fieldList );
            underlyingTables.put( algNode.getTable().getTable().getTableId(), underlyingColumns );
        }
        if ( algNode instanceof BiAlg ) {
            findUnderlyingTablesOfView( ((BiAlg) algNode).getLeft(), underlyingTables, fieldList );
            findUnderlyingTablesOfView( ((BiAlg) algNode).getRight(), underlyingTables, fieldList );
        } else if ( algNode instanceof SingleAlg ) {
            findUnderlyingTablesOfView( ((SingleAlg) algNode).getInput(), underlyingTables, fieldList );
        }
        return underlyingTables;
    }


    private List<Long> getUnderlyingColumns( AlgNode algNode, AlgDataType fieldList ) {
        List<Long> columnIds = ((LogicalTable) algNode.getTable().getTable()).getColumnIds();
        List<String> logicalColumnNames = ((LogicalTable) algNode.getTable().getTable()).getLogicalColumnNames();
        List<Long> underlyingColumns = new ArrayList<>();
        for ( int i = 0; i < columnIds.size(); i++ ) {
            for ( AlgDataTypeField algDataTypeField : fieldList.getFieldList() ) {
                String name = logicalColumnNames.get( i );
                if ( algDataTypeField.getName().equals( name ) ) {
                    underlyingColumns.add( columnIds.get( i ) );
                }
            }
        }
        return underlyingColumns;
    }


    @Override
    public void createEntity( long schemaId, String name, List<FieldInformation> fields, List<ConstraintInformation> constraints, boolean ifNotExists, List<DataStore> stores, PlacementType placementType, Statement statement ) throws EntityAlreadyExistsException {
        try {
            // Check if there is already an entity with this name
            if ( catalog.checkIfExistsEntity( schemaId, name ) ) {
                if ( ifNotExists ) {
                    // It is ok that there is already a table with this name because "IF NOT EXISTS" was specified
                    return;
                } else {
                    throw new EntityAlreadyExistsException();
                }
            }

            fields = new ArrayList<>( fields );
            constraints = new ArrayList<>( constraints );

            checkDocumentModel( schemaId, fields, constraints );

            boolean foundPk = false;
            for ( ConstraintInformation constraintInformation : constraints ) {
                if ( constraintInformation.type == ConstraintType.PRIMARY ) {
                    if ( foundPk ) {
                        throw new RuntimeException( "More than one primary key has been provided!" );
                    } else {
                        foundPk = true;
                    }
                }
            }
            if ( !foundPk ) {
                throw new RuntimeException( "No primary key has been provided!" );
            }

            if ( stores == null ) {
                // Ask router on which store(s) the table should be placed
                stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewTable();
            }

            long tableId = catalog.addEntity(
                    name,
                    schemaId,
                    statement.getPrepareContext().getCurrentUserId(),
                    EntityType.ENTITY,
                    true );

            // Initially create DataPlacement containers on every store the table should be placed.
            stores.forEach( store -> catalog.addDataPlacement( store.getAdapterId(), tableId ) );

            for ( FieldInformation information : fields ) {
                addField( information.name, information.typeInformation, information.collation, information.defaultValue, tableId, information.position, stores, placementType );
            }

            for ( ConstraintInformation constraint : constraints ) {
                addConstraint( constraint.name, constraint.type, constraint.columnNames, tableId );
            }

            //catalog.updateTablePartitionProperties(tableId, partitionProperty);
            CatalogEntity catalogEntity = catalog.getTable( tableId );

            // Trigger rebuild of schema; triggers schema creation on adapters
            PolySchemaBuilder.getInstance().getCurrent();

            for ( DataStore store : stores ) {
                catalog.addPartitionPlacement(
                        store.getAdapterId(),
                        catalogEntity.id,
                        catalogEntity.partitionProperty.partitionIds.get( 0 ),
                        PlacementType.AUTOMATIC,
                        null,
                        null,
                        DataPlacementRole.UPTODATE );

                store.createTable( statement.getPrepareContext(), catalogEntity, catalogEntity.partitionProperty.partitionIds );
            }

        } catch ( GenericCatalogException | UnknownColumnException | UnknownCollationException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void createCollection( long schemaId, String name, boolean ifNotExists, List<DataStore> stores, PlacementType placementType, Statement statement ) throws EntityAlreadyExistsException {
        // Check if there is already an entity with this name
        if ( catalog.checkIfExistsEntity( schemaId, name ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a table with this name because "IF NOT EXISTS" was specified
                return;
            } else {
                throw new EntityAlreadyExistsException();
            }
        }

        if ( stores == null ) {
            // Ask router on which store(s) the table should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewTable();
        }

        long collectionId = catalog.addCollection(
                name,
                schemaId,
                statement.getPrepareContext().getCurrentUserId(),
                EntityType.ENTITY,
                true );

        try {
            catalog.addDocumentLogistics( schemaId, collectionId, name, stores );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }

        // Initially create DataPlacement containers on every store the table should be placed.
        //stores.forEach( store -> catalog.addDataPlacement( store.getAdapterId(), collectionId ) );

        CatalogCollection catalogCollection = catalog.getCollection( collectionId );

        // Trigger rebuild of schema; triggers schema creation on adapters
        PolySchemaBuilder.getInstance().getCurrent();

        for ( DataStore store : stores ) {
            catalog.addDocumentPlacement(
                    store.getAdapterId(),
                    catalogCollection.id,
                    PlacementType.AUTOMATIC );

            afterDocumentLogistics( store, collectionId );

            store.createCollection( statement.getPrepareContext(), catalogCollection );
        }

    }


    private void afterDocumentLogistics( DataStore store, long collectionId ) {
        CatalogDocumentMapping mapping = catalog.getDocumentMapping( collectionId );
        CatalogEntity table = catalog.getTable( mapping.tableId );

        catalog.addPartitionPlacement(
                store.getAdapterId(),
                table.id,
                table.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );
    }


    private void checkDocumentModel( long schemaId, List<FieldInformation> columns, List<ConstraintInformation> constraints ) {
        if ( catalog.getNamespace( schemaId ).namespaceType == NamespaceType.DOCUMENT ) {
            List<String> names = columns.stream().map( c -> c.name ).collect( Collectors.toList() );

            if ( names.contains( "_id" ) ) {
                int index = names.indexOf( "_id" );
                columns.remove( index );
                constraints.remove( index );
                names.remove( "_id" );
            }

            // Add _id column if necessary
            if ( !names.contains( "_id" ) ) {
                ColumnTypeInformation typeInformation = new ColumnTypeInformation( PolyType.VARCHAR, PolyType.VARCHAR, 24, null, null, null, false );
                columns.add( new FieldInformation( "_id", typeInformation, Collation.CASE_INSENSITIVE, null, 0 ) );

            }

            // Remove any primaries
            List<ConstraintInformation> primaries = constraints.stream().filter( c -> c.type == ConstraintType.PRIMARY ).collect( Collectors.toList() );
            if ( primaries.size() > 0 ) {
                primaries.forEach( constraints::remove );
            }

            // Add constraint for _id as primary if necessary
            if ( constraints.stream().noneMatch( c -> c.type == ConstraintType.PRIMARY ) ) {
                constraints.add( new ConstraintInformation( "primary", ConstraintType.PRIMARY, Collections.singletonList( "_id" ) ) );
            }

            if ( names.contains( "_data" ) ) {
                columns.remove( names.indexOf( "_data" ) );
                names.remove( "_data" );
            }

            // Add _data column if necessary
            if ( !names.contains( "_data" ) ) {
                ColumnTypeInformation typeInformation = new ColumnTypeInformation( PolyType.JSON, PolyType.JSON, 1024, null, null, null, false );//new ColumnTypeInformation( PolyType.JSON, PolyType.JSON, 1024, null, null, null, false );
                columns.add( new FieldInformation( "_data", typeInformation, Collation.CASE_INSENSITIVE, null, 1 ) );
            }/*
            columns.clear();

            ColumnTypeInformation typeInformation = new ColumnTypeInformation( PolyType.DOCUMENT, PolyType.DOCUMENT, null, null, null, null, false );//new ColumnTypeInformation( PolyType.JSON, PolyType.JSON, 1024, null, null, null, false );
            columns.add( new FieldInformation( "_document", typeInformation, Collation.CASE_INSENSITIVE, null, 1 ) );

            // Remove any primaries
            List<ConstraintInformation> primaries = constraints.stream().filter( c -> c.type == ConstraintType.PRIMARY ).collect( Collectors.toList() );
            if ( primaries.size() > 0 ) {
                primaries.forEach( constraints::remove );
            }
            */
        }
    }


    @Override
    public void addPartitioning( PartitionInformation partitionInfo, List<DataStore> stores, Statement statement ) throws GenericCatalogException, UnknownPartitionTypeException, UnknownColumnException, PartitionGroupNamesNotUniqueException {
        CatalogColumn catalogColumn = catalog.getField( partitionInfo.table.id, partitionInfo.columnName );

        PartitionType actualPartitionType = PartitionType.getByName( partitionInfo.typeName );

        // Convert partition names and check whether they are unique
        List<String> sanitizedPartitionGroupNames = partitionInfo.partitionGroupNames
                .stream()
                .map( name -> name.trim().toLowerCase() )
                .collect( Collectors.toList() );
        if ( sanitizedPartitionGroupNames.size() != new HashSet<>( sanitizedPartitionGroupNames ).size() ) {
            throw new PartitionGroupNamesNotUniqueException();
        }

        // Check if specified partitionColumn is even part of the table
        if ( log.isDebugEnabled() ) {
            log.debug( "Creating partition group for table: {} with id {} on schema: {} on column: {}", partitionInfo.table.name, partitionInfo.table.id, partitionInfo.table.getNamespaceName(), catalogColumn.id );
        }

        CatalogEntity unPartitionedTable = catalog.getTable( partitionInfo.table.id );

        // Get partition manager
        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( actualPartitionType );

        // Check whether partition function supports type of partition column
        if ( !partitionManager.supportsColumnOfType( catalogColumn.type ) ) {
            throw new RuntimeException( "The partition function " + actualPartitionType + " does not support columns of type " + catalogColumn.type );
        }

        int numberOfPartitionGroups = partitionInfo.numberOfPartitionGroups;
        // Calculate how many partitions exist if partitioning is applied.
        long partId;
        if ( partitionInfo.partitionGroupNames.size() >= 2 && partitionInfo.numberOfPartitionGroups == 0 ) {
            numberOfPartitionGroups = partitionInfo.partitionGroupNames.size();
        }

        int numberOfPartitions = partitionInfo.numberOfPartitions;
        int numberOfPartitionsPerGroup = partitionManager.getNumberOfPartitionsPerGroup( numberOfPartitions );

        if ( partitionManager.requiresUnboundPartitionGroup() ) {
            // Because of the implicit unbound partition
            numberOfPartitionGroups = partitionInfo.partitionGroupNames.size();
            numberOfPartitionGroups += 1;
        }

        // Validate partition setup
        if ( !partitionManager.validatePartitionGroupSetup( partitionInfo.qualifiers, numberOfPartitionGroups, partitionInfo.partitionGroupNames, catalogColumn ) ) {
            throw new RuntimeException( "Partitioning failed for table: " + partitionInfo.table.name );
        }

        // Loop over value to create those partitions with partitionKey to uniquelyIdentify partition
        List<Long> partitionGroupIds = new ArrayList<>();
        for ( int i = 0; i < numberOfPartitionGroups; i++ ) {
            String partitionGroupName;

            // Make last partition unbound partition
            if ( partitionManager.requiresUnboundPartitionGroup() && i == numberOfPartitionGroups - 1 ) {
                partId = catalog.addPartitionGroup(
                        partitionInfo.table.id,
                        "Unbound",
                        partitionInfo.table.namespaceId,
                        actualPartitionType,
                        numberOfPartitionsPerGroup,
                        new ArrayList<>(),
                        true );
            } else {
                // If no names have been explicitly defined
                if ( partitionInfo.partitionGroupNames.isEmpty() ) {
                    partitionGroupName = "part_" + i;
                } else {
                    partitionGroupName = partitionInfo.partitionGroupNames.get( i );
                }

                // Mainly needed for HASH
                if ( partitionInfo.qualifiers.isEmpty() ) {
                    partId = catalog.addPartitionGroup(
                            partitionInfo.table.id,
                            partitionGroupName,
                            partitionInfo.table.namespaceId,
                            actualPartitionType,
                            numberOfPartitionsPerGroup,
                            new ArrayList<>(),
                            false );
                } else {
                    partId = catalog.addPartitionGroup(
                            partitionInfo.table.id,
                            partitionGroupName,
                            partitionInfo.table.namespaceId,
                            actualPartitionType,
                            numberOfPartitionsPerGroup,
                            partitionInfo.qualifiers.get( i ),
                            false );
                }
            }
            partitionGroupIds.add( partId );
        }

        List<Long> partitionIds = new ArrayList<>();
        //get All PartitionGroups and then get all partitionIds  for each PG and add them to completeList of partitionIds
        //catalog.getPartitionGroups( partitionInfo.table.id ).forEach( pg -> partitionIds.forEach( p -> partitionIds.add( p ) ) );
        partitionGroupIds.forEach( pg -> catalog.getPartitions( pg ).forEach( p -> partitionIds.add( p.id ) ) );

        PartitionProperty partitionProperty;
        if ( actualPartitionType == PartitionType.TEMPERATURE ) {
            long frequencyInterval = ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getInterval();
            switch ( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getIntervalUnit().toString() ) {
                case "days":
                    frequencyInterval = frequencyInterval * 60 * 60 * 24;
                    break;

                case "hours":
                    frequencyInterval = frequencyInterval * 60 * 60;
                    break;

                case "minutes":
                    frequencyInterval = frequencyInterval * 60;
                    break;
            }

            int hotPercentageIn = Integer.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getHotAccessPercentageIn().toString() );
            int hotPercentageOut = Integer.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getHotAccessPercentageOut().toString() );

            //Initially distribute partitions as intended in a running system
            long numberOfPartitionsInHot = numberOfPartitions * hotPercentageIn / 100;
            if ( numberOfPartitionsInHot == 0 ) {
                numberOfPartitionsInHot = 1;
            }

            long numberOfPartitionsInCold = numberOfPartitions - numberOfPartitionsInHot;

            // -1 because one partition is already created in COLD
            List<Long> partitionsForHot = new ArrayList<>();
            catalog.getPartitions( partitionGroupIds.get( 0 ) ).forEach( p -> partitionsForHot.add( p.id ) );

            // -1 because one partition is already created in HOT
            for ( int i = 0; i < numberOfPartitionsInHot - 1; i++ ) {
                long tempId;
                tempId = catalog.addPartition( partitionInfo.table.id, partitionInfo.table.namespaceId, partitionGroupIds.get( 0 ), partitionInfo.qualifiers.get( 0 ), false );
                partitionIds.add( tempId );
                partitionsForHot.add( tempId );
            }

            catalog.updatePartitionGroup( partitionGroupIds.get( 0 ), partitionsForHot );

            // -1 because one partition is already created in COLD
            List<Long> partitionsForCold = new ArrayList<>();
            catalog.getPartitions( partitionGroupIds.get( 1 ) ).forEach( p -> partitionsForCold.add( p.id ) );

            for ( int i = 0; i < numberOfPartitionsInCold - 1; i++ ) {
                long tempId;
                tempId = catalog.addPartition( partitionInfo.table.id, partitionInfo.table.namespaceId, partitionGroupIds.get( 1 ), partitionInfo.qualifiers.get( 1 ), false );
                partitionIds.add( tempId );
                partitionsForCold.add( tempId );
            }

            catalog.updatePartitionGroup( partitionGroupIds.get( 1 ), partitionsForCold );

            partitionProperty = TemperaturePartitionProperty.builder()
                    .partitionType( actualPartitionType )
                    .isPartitioned( true )
                    .internalPartitionFunction( PartitionType.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getInternalPartitionFunction().toString().toUpperCase() ) )
                    .partitionColumnId( catalogColumn.id )
                    .partitionGroupIds( ImmutableList.copyOf( partitionGroupIds ) )
                    .partitionIds( ImmutableList.copyOf( partitionIds ) )
                    .partitionCostIndication( PartitionCostIndication.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getAccessPattern().toString().toUpperCase() ) )
                    .frequencyInterval( frequencyInterval )
                    .hotAccessPercentageIn( hotPercentageIn )
                    .hotAccessPercentageOut( hotPercentageOut )
                    .reliesOnPeriodicChecks( true )
                    .hotPartitionGroupId( partitionGroupIds.get( 0 ) )
                    .coldPartitionGroupId( partitionGroupIds.get( 1 ) )
                    .numPartitions( partitionIds.size() )
                    .numPartitionGroups( partitionGroupIds.size() )
                    .build();
        } else {
            partitionProperty = PartitionProperty.builder()
                    .partitionType( actualPartitionType )
                    .isPartitioned( true )
                    .partitionColumnId( catalogColumn.id )
                    .partitionGroupIds( ImmutableList.copyOf( partitionGroupIds ) )
                    .partitionIds( ImmutableList.copyOf( partitionIds ) )
                    .reliesOnPeriodicChecks( false )
                    .build();
        }

        // Update catalog table
        catalog.partitionTable( partitionInfo.table.id, actualPartitionType, catalogColumn.id, numberOfPartitionGroups, partitionGroupIds, partitionProperty );

        // Get primary key of table and use PK to find all DataPlacements of table
        long pkid = partitionInfo.table.primaryKey;
        List<Long> pkColumnIds = catalog.getPrimaryKey( pkid ).columnIds;
        // Basically get first part of PK even if its compound of PK it is sufficient
        CatalogColumn pkColumn = catalog.getField( pkColumnIds.get( 0 ) );
        // This gets us only one ccp per store (first part of PK)

        boolean fillStores = false;
        if ( stores == null ) {
            stores = new ArrayList<>();
            fillStores = true;
        }
        List<CatalogColumnPlacement> catalogColumnPlacements = catalog.getColumnPlacement( pkColumn.id );
        for ( CatalogColumnPlacement ccp : catalogColumnPlacements ) {
            if ( fillStores ) {
                // Ask router on which store(s) the table should be placed
                Adapter adapter = AdapterManager.getInstance().getAdapter( ccp.adapterId );
                if ( adapter instanceof DataStore ) {
                    stores.add( (DataStore) adapter );
                }
            }
        }

        // Now get the partitioned table, partitionInfo still contains the basic/unpartitioned table.
        CatalogEntity partitionedTable = catalog.getTable( partitionInfo.table.id );
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        for ( DataStore store : stores ) {
            for ( long partitionId : partitionIds ) {
                catalog.addPartitionPlacement(
                        store.getAdapterId(),
                        partitionedTable.id,
                        partitionId,
                        PlacementType.AUTOMATIC,
                        null,
                        null,
                        DataPlacementRole.UPTODATE );
            }

            // First create new tables
            store.createTable( statement.getPrepareContext(), partitionedTable, partitionedTable.partitionProperty.partitionIds );

            // Copy data from unpartitioned to partitioned
            // Get only columns that are actually on that store
            // Every store of a newly partitioned table, initially will hold all partitions
            List<CatalogColumn> necessaryColumns = new LinkedList<>();
            catalog.getColumnPlacementsOnAdapterPerTable( store.getAdapterId(), partitionedTable.id ).forEach( cp -> necessaryColumns.add( catalog.getField( cp.columnId ) ) );

            // Copy data from the old partition to new partitions
            dataMigrator.copyPartitionData(
                    statement.getTransaction(),
                    catalog.getAdapter( store.getAdapterId() ),
                    unPartitionedTable,
                    partitionedTable,
                    necessaryColumns,
                    unPartitionedTable.partitionProperty.partitionIds,
                    partitionedTable.partitionProperty.partitionIds );
        }
        // Remove old tables
        stores.forEach( store -> store.dropTable( statement.getPrepareContext(), unPartitionedTable, unPartitionedTable.partitionProperty.partitionIds ) );
        catalog.deletePartitionGroup( unPartitionedTable.id, unPartitionedTable.namespaceId, unPartitionedTable.partitionProperty.partitionGroupIds.get( 0 ) );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void removePartitioning( CatalogEntity partitionedTable, Statement statement ) {
        long tableId = partitionedTable.id;

        if ( log.isDebugEnabled() ) {
            log.debug( "Merging partitions for table: {} with id {} on schema: {}",
                    partitionedTable.name, partitionedTable.id, partitionedTable.getNamespaceName() );
        }

        // Need to gather the partitionDistribution before actually merging
        // We need a columnPlacement for every partition
        Map<Long, List<CatalogColumnPlacement>> placementDistribution = new HashMap<>();
        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( partitionedTable.partitionProperty.partitionType );
        placementDistribution = partitionManager.getRelevantPlacements( partitionedTable, partitionedTable.partitionProperty.partitionIds, new ArrayList<>( Arrays.asList( -1 ) ) );

        // Update catalog table
        catalog.mergeTable( tableId );

        // Now get the merged table
        CatalogEntity mergedTable = catalog.getTable( tableId );

        List<DataStore> stores = new ArrayList<>();
        // Get primary key of table and use PK to find all DataPlacements of table
        long pkid = partitionedTable.primaryKey;
        List<Long> pkColumnIds = catalog.getPrimaryKey( pkid ).columnIds;
        // Basically get first part of PK even if its compound of PK it is sufficient
        CatalogColumn pkColumn = catalog.getField( pkColumnIds.get( 0 ) );
        // This gets us only one ccp per store (first part of PK)

        List<CatalogColumnPlacement> catalogColumnPlacements = catalog.getColumnPlacement( pkColumn.id );
        for ( CatalogColumnPlacement ccp : catalogColumnPlacements ) {
            // Ask router on which store(s) the table should be placed
            Adapter adapter = AdapterManager.getInstance().getAdapter( ccp.adapterId );
            if ( adapter instanceof DataStore ) {
                stores.add( (DataStore) adapter );
            }
        }

        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();

        // For merge create only full placements on the used stores. Otherwise partition constraints might not hold
        for ( DataStore store : stores ) {
            // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
            catalog.addPartitionPlacement(
                    store.getAdapterId(),
                    mergedTable.id,
                    mergedTable.partitionProperty.partitionIds.get( 0 ),
                    PlacementType.AUTOMATIC,
                    null,
                    null,
                    DataPlacementRole.UPTODATE );

            // First create new tables
            store.createTable( statement.getPrepareContext(), mergedTable, mergedTable.partitionProperty.partitionIds );

            // Get only columns that are actually on that store
            List<CatalogColumn> necessaryColumns = new LinkedList<>();
            catalog.getColumnPlacementsOnAdapterPerTable( store.getAdapterId(), mergedTable.id ).forEach( cp -> necessaryColumns.add( catalog.getField( cp.columnId ) ) );

            // TODO @HENNLO Check if this can be omitted
            catalog.updateDataPlacement( store.getAdapterId(), mergedTable.id,
                    catalog.getDataPlacement( store.getAdapterId(), mergedTable.id ).columnPlacementsOnAdapter,
                    mergedTable.partitionProperty.partitionIds );
            //

            dataMigrator.copySelectiveData(
                    statement.getTransaction(),
                    catalog.getAdapter( store.getAdapterId() ),
                    partitionedTable,
                    mergedTable,
                    necessaryColumns,
                    placementDistribution,
                    mergedTable.partitionProperty.partitionIds );
        }

        // Needs to be separated from loop above. Otherwise we loose data
        for ( DataStore store : stores ) {
            List<Long> partitionIdsOnStore = new ArrayList<>();
            catalog.getPartitionPlacementsByTableOnAdapter( store.getAdapterId(), partitionedTable.id ).forEach( p -> partitionIdsOnStore.add( p.partitionId ) );
            // Otherwise everything will be dropped again, leaving the table inaccessible
            partitionIdsOnStore.remove( mergedTable.partitionProperty.partitionIds.get( 0 ) );

            // Drop all partitionedTables (table contains old partitionIds)
            store.dropTable( statement.getPrepareContext(), partitionedTable, partitionIdsOnStore );
        }
        // Loop over **old.partitionIds** to delete all partitions which are part of table
        // Needs to be done separately because partitionPlacements will be recursively dropped in `deletePartitionGroup` but are needed in dropTable
        for ( long partitionGroupId : partitionedTable.partitionProperty.partitionGroupIds ) {
            catalog.deletePartitionGroup( tableId, partitionedTable.namespaceId, partitionGroupId );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private void addField( String columnName, ColumnTypeInformation typeInformation, Collation collation, String defaultValue, long tableId, int position, List<DataStore> stores, PlacementType placementType ) throws GenericCatalogException, UnknownCollationException, UnknownColumnException {
        long addedColumnId = catalog.addColumn(
                columnName,
                tableId,
                position,
                typeInformation.type,
                typeInformation.collectionType,
                typeInformation.precision,
                typeInformation.scale,
                typeInformation.dimension,
                typeInformation.cardinality,
                typeInformation.nullable,
                collation
        );

        // Add default value
        addDefaultValue( defaultValue, addedColumnId );

        for ( DataStore s : stores ) {
            catalog.addColumnPlacement(
                    s.getAdapterId(),
                    addedColumnId,
                    placementType,
                    null,
                    null,
                    null
            );
        }
    }


    @Override
    public void addConstraint( String constraintName, ConstraintType constraintType, List<String> columnNames, long tableId ) throws UnknownColumnException, GenericCatalogException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            CatalogColumn catalogColumn = catalog.getField( tableId, columnName );
            columnIds.add( catalogColumn.id );
        }
        if ( constraintType == ConstraintType.PRIMARY ) {
            catalog.addPrimaryKey( tableId, columnIds );
        } else if ( constraintType == ConstraintType.UNIQUE ) {
            if ( constraintName == null ) {
                constraintName = NameGenerator.generateConstraintName();
            }
            catalog.addUniqueConstraint( tableId, constraintName, columnIds );
        }
    }


    @Override
    public void dropSchema( long databaseId, String schemaName, boolean ifExists, Statement statement ) throws SchemaNotExistException, DdlOnSourceException {
        try {
            // Check if there is a schema with this name
            if ( catalog.checkIfExistsNamespace( databaseId, schemaName ) ) {
                CatalogNamespace catalogNamespace = catalog.getNamespace( databaseId, schemaName );

                // Drop all tables in this schema
                List<CatalogEntity> catalogEntities = catalog.getTables( catalogNamespace.id, null );
                for ( CatalogEntity catalogEntity : catalogEntities ) {
                    dropTable( catalogEntity, statement );
                }

                // Drop schema
                catalog.deleteSchema( catalogNamespace.id );
            } else {
                if ( ifExists ) {
                    // This is ok because "IF EXISTS" was specified
                    return;
                } else {
                    throw new SchemaNotExistException();
                }
            }
        } catch ( UnknownNamespaceException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropView( CatalogEntity catalogView, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type VIEW
        if ( catalogView.entityType == EntityType.VIEW ) {
            // Empty on purpose
        } else {
            throw new NotViewException();
        }

        // Check if views are dependent from this view
        checkViewDependencies( catalogView );

        catalog.flagTableForDeletion( catalogView.id, true );
        catalog.deleteViewDependencies( (CatalogView) catalogView );

        // Delete columns
        for ( Long columnId : catalogView.fieldIds ) {
            catalog.deleteColumn( columnId );
        }

        // Delete the view
        catalog.deleteTable( catalogView.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropMaterializedView( CatalogEntity materializedView, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type Materialized View
        if ( materializedView.entityType == EntityType.MATERIALIZED_VIEW ) {
            // Empty on purpose
        } else {
            throw new NotMaterializedViewException();
        }
        // Check if views are dependent from this view
        checkViewDependencies( materializedView );

        catalog.flagTableForDeletion( materializedView.id, true );

        catalog.deleteViewDependencies( (CatalogView) materializedView );

        dropTable( materializedView, statement );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropTable( CatalogEntity catalogEntity, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        //checkIfDdlPossible( catalogEntity.tableType );

        // Check if views dependent on this table
        checkViewDependencies( catalogEntity );

        // Check if there are foreign keys referencing this table
        List<CatalogForeignKey> selfRefsToDelete = new LinkedList<>();
        List<CatalogForeignKey> exportedKeys = catalog.getExportedKeys( catalogEntity.id );
        if ( exportedKeys.size() > 0 ) {
            for ( CatalogForeignKey foreignKey : exportedKeys ) {
                if ( foreignKey.tableId == catalogEntity.id ) {
                    // If this is a self-reference, drop it later.
                    selfRefsToDelete.add( foreignKey );
                } else {
                    throw new PolyphenyDbException( "Cannot drop table '" + catalogEntity.getNamespaceName() + "." + catalogEntity.name + "' because it is being referenced by '" + exportedKeys.get( 0 ).getSchemaName() + "." + exportedKeys.get( 0 ).getTableName() + "'." );
                }
            }
        }

        // Make sure that all adapters are of type store (and not source)
        for ( int storeId : catalogEntity.dataPlacements ) {
            getDataStoreInstance( storeId );
        }

        // Delete all indexes
        for ( CatalogIndex index : catalog.getIndexes( catalogEntity.id, false ) ) {
            if ( index.location == 0 ) {
                // Delete polystore index
                IndexManager.getInstance().deleteIndex( index );
            } else {
                // Delete index on store
                AdapterManager.getInstance().getStore( index.location ).dropIndex(
                        statement.getPrepareContext(),
                        index,
                        catalog.getPartitionsOnDataPlacement( index.location, catalogEntity.id ) );
            }
            // Delete index in catalog
            catalog.deleteIndex( index.id );
        }

        // Delete data from the stores and remove the column placement
        catalog.flagTableForDeletion( catalogEntity.id, true );
        for ( int storeId : catalogEntity.dataPlacements ) {
            // Delete table on store
            List<Long> partitionIdsOnStore = new ArrayList<>();
            catalog.getPartitionPlacementsByTableOnAdapter( storeId, catalogEntity.id ).forEach( p -> partitionIdsOnStore.add( p.partitionId ) );

            AdapterManager.getInstance().getStore( storeId ).dropTable( statement.getPrepareContext(), catalogEntity, partitionIdsOnStore );
            // Delete column placement in catalog
            for ( Long columnId : catalogEntity.fieldIds ) {
                if ( catalog.checkIfExistsColumnPlacement( storeId, columnId ) ) {
                    catalog.deleteColumnPlacement( storeId, columnId, false );
                }
            }
        }

        // Delete the self-referencing foreign keys
        try {
            for ( CatalogForeignKey foreignKey : selfRefsToDelete ) {
                catalog.deleteForeignKey( foreignKey.id );
            }
        } catch ( GenericCatalogException e ) {
            catalog.flagTableForDeletion( catalogEntity.id, true );
            throw new PolyphenyDbContextException( "Exception while deleting self-referencing foreign key constraints.", e );
        }

        // Delete indexes of this table
        List<CatalogIndex> indexes = catalog.getIndexes( catalogEntity.id, false );
        for ( CatalogIndex index : indexes ) {
            catalog.deleteIndex( index.id );
            IndexManager.getInstance().deleteIndex( index );
        }

        // Delete keys and constraints
        try {
            // Remove primary key
            catalog.deletePrimaryKey( catalogEntity.id );
            // Delete all foreign keys of the table
            List<CatalogForeignKey> foreignKeys = catalog.getForeignKeys( catalogEntity.id );
            for ( CatalogForeignKey foreignKey : foreignKeys ) {
                catalog.deleteForeignKey( foreignKey.id );
            }
            // Delete all constraints of the table
            for ( CatalogConstraint constraint : catalog.getConstraints( catalogEntity.id ) ) {
                catalog.deleteConstraint( constraint.id );
            }
        } catch ( GenericCatalogException e ) {
            catalog.flagTableForDeletion( catalogEntity.id, true );
            throw new PolyphenyDbContextException( "Exception while dropping keys.", e );
        }

        // Delete columns
        for ( Long columnId : catalogEntity.fieldIds ) {
            catalog.deleteColumn( columnId );
        }

        // Delete the table
        catalog.deleteTable( catalogEntity.id );

        // Monitor dropTables for statistics
        prepareMonitoring( statement, Kind.DROP_TABLE, catalogEntity );

        // ON_COMMIT constraint needs no longer to be enforced if entity does no longer exist
        statement.getTransaction().getCatalogTables().remove( catalogEntity );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void truncate( CatalogEntity catalogEntity, Statement statement ) {
        // Make sure that the table can be modified
        if ( !catalogEntity.modifiable ) {
            throw new RuntimeException( "Unable to modify a read-only table!" );
        }

        // Monitor truncate for rowCount
        prepareMonitoring( statement, Kind.TRUNCATE, catalogEntity );

        //  Execute truncate on all placements
        catalogEntity.dataPlacements.forEach( adapterId -> {
            AdapterManager.getInstance().getAdapter( adapterId ).truncate( statement.getPrepareContext(), catalogEntity );
        } );
    }


    private void prepareMonitoring( Statement statement, Kind kind, CatalogEntity catalogEntity ) {
        prepareMonitoring( statement, kind, catalogEntity, null );
    }


    private void prepareMonitoring( Statement statement, Kind kind, CatalogEntity catalogEntity, CatalogColumn catalogColumn ) {
        // Initialize Monitoring
        if ( statement.getMonitoringEvent() == null ) {
            StatementEvent event = new DdlEvent();
            event.setMonitoringType( kind.name() );
            event.setTableId( catalogEntity.id );
            event.setSchemaId( catalogEntity.namespaceId );
            if ( kind == Kind.DROP_COLUMN ) {
                event.setColumnId( catalogColumn.id );
            }
            statement.setMonitoringEvent( event );
        }
    }


    @Override
    public void dropFunction() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void setOption() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void createType() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void dropType() {
        throw new RuntimeException( "Not supported yet" );
    }

}
