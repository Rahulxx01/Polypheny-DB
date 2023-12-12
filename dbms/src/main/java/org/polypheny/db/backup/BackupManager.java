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

package org.polypheny.db.backup;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.backup.datagatherer.entryGatherer.GatherEntries;
import org.polypheny.db.backup.datagatherer.GatherSchema;
import org.polypheny.db.backup.datainserter.InsertEntries;
import org.polypheny.db.backup.datainserter.InsertSchema;
import org.polypheny.db.backup.dependencies.DependencyManager;
import org.polypheny.db.backup.dependencies.EntityReferencer;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.catalog.entity.logical.LogicalForeignKey;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.information.*;
import org.polypheny.db.transaction.TransactionManager;
import org.polypheny.db.util.Pair;


@Slf4j
public class BackupManager {


    @Getter
    private static BackupManager INSTANCE = null;
    private InformationPage informationPage;
    private InformationGroup informationGroupOverview;
    @Getter
    private BackupInformationObject backupInformationObject;
    public static TransactionManager transactionManager = null;
    public static int batchSize = 2;  //#rows (100 for the beginning)
    public static int threadNumber = 8; //#cores (#cpu's) for now
    //private final Logger logger;


    public BackupManager( TransactionManager transactionManager ) {
        this.transactionManager = transactionManager;

        informationPage = new InformationPage( "Backup Tasks" );
        informationPage.fullWidth();
        informationGroupOverview = new InformationGroup( informationPage, "Overview" );

        // datagatherer.GatherEntries gatherEntries = new datagatherer.GatherEntries();
        //GatherEntries gatherEntries = new GatherEntries();

        InformationManager im = InformationManager.getInstance();
        im.addPage( informationPage );
        im.addGroup( informationGroupOverview );

        // start backup button
        InformationText startBackup = new InformationText( informationGroupOverview, "Create the Backup." );
        startBackup.setOrder( 1 );
        im.registerInformation( startBackup );

        InformationAction startBackupAction = new InformationAction( informationGroupOverview, "Start", parameters -> {
            //IndexManager.getInstance().resetCounters();
            startDataGathering();
            System.out.println( "gather" );
            return "Successfully started backup";
        } );
        startBackupAction.setOrder( 2 );
        im.registerInformation( startBackupAction );

        // insert backup-data button
        InformationText insertBackupData = new InformationText( informationGroupOverview, "Insert the Backup Data." );
        insertBackupData.setOrder( 3 );
        im.registerInformation( insertBackupData );

        InformationAction insertBackupDataAction = new InformationAction( informationGroupOverview, "Insert", parameters -> {
            //IndexManager.getInstance().resetCounters();
            startInserting();
            System.out.println( "hii" );
            return "Successfully inserted backup data";
        } );
        insertBackupDataAction.setOrder( 4 );
        im.registerInformation( insertBackupDataAction );

    }


    public static BackupManager setAndGetInstance( BackupManager backupManager ) {
        if ( INSTANCE != null ) {
            throw new GenericRuntimeException( "Setting the BackupInterface, when already set is not permitted." );
        }
        INSTANCE = backupManager;
        return INSTANCE;
    }


    public void startDataGathering() {
        this.backupInformationObject = new BackupInformationObject();
        GatherSchema gatherSchema = new GatherSchema();

        //gatherEntries.start();
        this.backupInformationObject = gatherSchema.start( backupInformationObject );
        wrapEntities();

        // how/where do i safe the data
        //gatherEntries.start();


        List<Pair<String, String>> tablesForDataCollection = tableDataToBeGathered();
        List<Pair<Long, String>> collectionsForDataCollection = collectionDataToBeGathered();
        List<Long> graphNamespaceIds = collectGraphNamespaceIds();
        GatherEntries gatherEntries = new GatherEntries(transactionManager, tablesForDataCollection, collectionsForDataCollection, graphNamespaceIds);
        gatherEntries.start();
        log.info( "finished all datagathering" );


    }


    private void wrapEntities() {
        // 1. check for dependencies
        // 2. wrap namespaces, tables, views, etc

        ImmutableMap<Long, List<LogicalForeignKey>> foreignKeysPerTable = backupInformationObject.getForeignKeysPerTable();
        Map<Long, List<Long>> namespaceDependencies = new HashMap<>();  // key: namespaceId, value: referencedKeySchemaId
        Map<Long, List<Long>> tableDependencies = new HashMap<>();  // key: tableId, value: referencedKeyTableId
        Map<Long, List<Pair<Long, Long>>> namespaceTableDependendencies = new HashMap<>();  // key: namespaceId, value: <namespaceId, referencedKeyTableId>
        Map<Long, List<Long>> viewDependencies = new HashMap<>();
        //TODO(FF): are there dependencies for collections? (views/indexes from collections?)

        //go through all foreign keys, and check if the namespaceId equals the referencedKeySchemaId, and if not, add it to the namespaceDependencies map, with the namespaceId as key and the referencedKeySchemaId as value
        for ( Map.Entry<Long, List<LogicalForeignKey>> entry : foreignKeysPerTable.entrySet() ) {
            for ( LogicalForeignKey logicalForeignKey : entry.getValue() ) {
                if ( logicalForeignKey.namespaceId != logicalForeignKey.referencedKeySchemaId ) {

                    // Check for namespace dependencies
                    if ( namespaceDependencies.containsKey( logicalForeignKey.namespaceId ) ) {
                        List<Long> temp = namespaceDependencies.get( logicalForeignKey.namespaceId );
                        //only add it if it isn't already in the list??
                        temp.add( logicalForeignKey.referencedKeySchemaId );
                        namespaceDependencies.put( logicalForeignKey.namespaceId, temp );
                    } else {
                        List<Long> temp = new ArrayList<>();
                        temp.add( logicalForeignKey.referencedKeySchemaId );
                        namespaceDependencies.put( logicalForeignKey.namespaceId, temp );
                    }

                    // Check for table dependencies
                    if ( tableDependencies.containsKey( logicalForeignKey.tableId ) ) {
                        List<Long> temp = tableDependencies.get( logicalForeignKey.tableId );
                        temp.add( logicalForeignKey.referencedKeyTableId );
                        tableDependencies.put( logicalForeignKey.tableId, temp );

                        List<Pair<Long, Long>> temp2 = namespaceTableDependendencies.get( logicalForeignKey.namespaceId );
                        temp2.add( new Pair<>( logicalForeignKey.referencedKeySchemaId, logicalForeignKey.referencedKeyTableId ) );
                        namespaceTableDependendencies.put( logicalForeignKey.namespaceId, temp2 );
                    } else {
                        List<Long> temp = new ArrayList<>();
                        temp.add( logicalForeignKey.referencedKeyTableId );
                        tableDependencies.put( logicalForeignKey.tableId, temp );

                        List<Pair<Long, Long>> temp2 = new ArrayList<>();
                        temp2.add( new Pair<>( logicalForeignKey.referencedKeySchemaId, logicalForeignKey.referencedKeyTableId ) );
                        namespaceTableDependendencies.put( logicalForeignKey.namespaceId, temp2 );
                    }


                }
            }
        }

        // wrap all namespaces with BackupEntityWrapper
        ImmutableMap<Long, BackupEntityWrapper<LogicalNamespace>> wrappedNamespaces = backupInformationObject.wrapNamespaces( backupInformationObject.getNamespaces(), namespaceDependencies, tableDependencies, namespaceTableDependendencies);
        backupInformationObject.setWrappedNamespaces( wrappedNamespaces );

        // wrap all tables with BackupEntityWrapper
        ImmutableMap<Long, List<BackupEntityWrapper<LogicalEntity>>> wrappedTables = backupInformationObject.wrapLogicalEntities( backupInformationObject.getTables(), tableDependencies, namespaceTableDependendencies, true);
        backupInformationObject.setWrappedTables( wrappedTables );

        // wrap all collections with BackupEntityWrapper
        ImmutableMap<Long, List<BackupEntityWrapper<LogicalEntity>>> wrappedCollections = backupInformationObject.wrapLogicalEntities( backupInformationObject.getCollections(), null, namespaceTableDependendencies, true);
        backupInformationObject.setWrappedCollections( wrappedCollections );

        /*
        ArrayList<LogicalTable> lol = new ArrayList<>();
        lol.add( (LogicalTable) backupInformationObject.getTables().get( 0 ));

        Map<Long, List<LogicalEntity>> lol2 = backupInformationObject.getTables();
        Map<Integer, List<LogicalEntity>> lol3 =  backupInformationObject.test2(lol2);


        //ImmutableMap<Integer, LogicalTable> ha = backupInformationObject.test( lol );

         */

        // testing
        DependencyManager dependencyManager = new DependencyManager();
        EntityReferencer entityReferencer = null;
        List<EntityReferencer> allTableReferencers = backupInformationObject.getAllTableReferencers();
        Map<Long, List<Long>> test = new HashMap<>();
        if (entityReferencer != null) {
            for ( EntityReferencer tableReferencer : allTableReferencers ) {
                List<Long> lol = dependencyManager.getReferencedEntities(entityReferencer, allTableReferencers );
                test.put( tableReferencer.getEntityId(), lol );
            }
        }



    }


    private void startInserting() {
        InsertSchema insertSchema = new InsertSchema( transactionManager );

        if ( backupInformationObject != null ) {
            insertSchema.start( backupInformationObject );
        } else {
            log.info( "backupInformationObject is null" );
        }


        InsertEntries insertEntries = new InsertEntries();
        insertEntries.start();
        log.info( "inserting done" );
    }


    /**
     * returns a list of all table names where the entry-data should be collected for the backup (right now, all of them, except sources)
     * @return list of pairs with the format: namespacename, tablename with all the names
     */
    private List<Pair<String, String>> tableDataToBeGathered() {
        List<Pair<String, String>> tableDataToBeGathered = new ArrayList<>();
        List<LogicalNamespace> relationalNamespaces = backupInformationObject.getRelNamespaces();

        if (!relationalNamespaces.isEmpty()) {
            for ( LogicalNamespace relationalNamespace : relationalNamespaces ) {
                List<LogicalEntity> tables = backupInformationObject.getTables().get( relationalNamespace.id );
                if(!tables.isEmpty() ) {
                    for ( LogicalEntity table : tables ) {
                        if (!(table.entityType.equals( EntityType.SOURCE ))) {
                            Pair pair = new Pair<>( relationalNamespace.name, table.name );
                            tableDataToBeGathered.add( pair );
                        }
                    }
                }
            }
        }
        /*
        for ( Map.Entry<Long, List<LogicalEntity>> entry : backupInformationObject.getTables().entrySet() ) {
            for ( LogicalEntity table : entry.getValue() ) {
                if (!(table.entityType.equals( EntityType.SOURCE ))) {
                    tableDataToBeGathered.add( relationalNamespace.name + "." + table.name );
                }
            }
        }

         */
        return tableDataToBeGathered;
    }


    /**
     * returns a list of pairs with all collection names and their corresponding namespaceId where the entry-data should be collected for the backup (right now all of them)
     * @return list of pairs with the format: <namespaceId, collectionName>
     */
    private List<Pair<Long, String>> collectionDataToBeGathered() {
        List<Pair<Long, String>> collectionDataToBeGathered = new ArrayList<>();

        for ( Map.Entry<Long, List<LogicalEntity>> entry : backupInformationObject.getCollections().entrySet() ) {
            for ( LogicalEntity collection : entry.getValue() ) {
                collectionDataToBeGathered.add( new Pair<>( entry.getKey(), collection.name ) );
            }
        }

        return collectionDataToBeGathered;
    }


    /**
     * gets a list of all graph namespaceIds
     * @return list of all graph namespaceIds
     */
    private List<Long> collectGraphNamespaceIds() {
        List<Long> graphNamespaceIds = new ArrayList<>();
        for ( Map.Entry<Long, LogicalEntity> entry : backupInformationObject.getGraphs().entrySet() ) {
            graphNamespaceIds.add( entry.getKey() );
        }
        return graphNamespaceIds;
    }

}
