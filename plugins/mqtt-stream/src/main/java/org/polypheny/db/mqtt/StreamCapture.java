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

package org.polypheny.db.mqtt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.NamespaceType;
import org.polypheny.db.catalog.Catalog.PlacementType;
import org.polypheny.db.catalog.entity.CatalogCollection;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.catalog.exceptions.EntityAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.NoTablePrimaryKeyException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.iface.QueryInterfaceManager;
import org.polypheny.db.prepare.Context;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.transaction.TransactionManager;
import org.polypheny.db.util.PolyphenyHomeDirManager;

@Slf4j
public class StreamCapture {

    TransactionManager transactionManager;
    PolyphenyHomeDirManager homeDirManager;
    PolyStream stream;


    StreamCapture( final TransactionManager transactionManager, PolyStream stream ) {
        this.transactionManager = transactionManager;
        this.stream = stream;
        Transaction transaction = getTransaction();
        Statement statement = transaction.createStatement();
        this.stream.setUserId( statement.getPrepareContext().getCurrentUserId() );
        this.stream.setDatabaseId( statement.getPrepareContext().getDatabaseId() );
    }

    public static boolean validateNamespaceName( String namespaceName, NamespaceType namespaceType ) {
        // TODO: Nachrichten an UI schicken falls Namespace name nicht geht
        boolean nameCanBeUsed = false;
        Catalog catalog = Catalog.getInstance();
        // TODO: database ID evtl von von statement.getPrepareContext().getCurrentUserId() und statement.getPrepareContext().getDatabaseId() nehmen?
        if ( catalog.checkIfExistsSchema( Catalog.defaultDatabaseId, namespaceName ) ) {
            CatalogSchema schema = null;
            try {
                schema = catalog.getSchema( Catalog.defaultDatabaseId, namespaceName );
            } catch ( UnknownSchemaException e ) {
                log.error( "The catalog seems to be corrupt, as it was impossible to retrieve an existing namespace." );
                return nameCanBeUsed;
            }
            assert schema != null;
            if ( schema.namespaceType == namespaceType ) {
                nameCanBeUsed = true;
            } else {
                log.info( "There is already a namespace existing in this database with the given name but of type {}.", schema.getNamespaceType() );
                log.info( "Please change the name or the type to {} to use the existing namespace.", schema.getNamespaceType() );
            }
        } else {
            nameCanBeUsed = true;
        }
        //TODO: rmv
        log.info( String.valueOf( nameCanBeUsed ) );
        return nameCanBeUsed;
    }


    void handleContent() {
        //String path = registerTopicFolder(topic);
        long storeId = getCollectionID();
        if ( storeId != 0 ) {
            stream.getTopic().storeID = storeId;
            boolean saved = saveContent();
            //TODO: gescheite Tests
//            Catalog catalog = Catalog.getInstance();
//            CatalogSchema schema = null;
//            schema = catalog.getSchema( stream.getNamespaceID() );
        }
    }


    /**
     * @return the id of the collection that was either already existing with the topic as name or that was newly created
     */
    long getCollectionID() {
        Catalog catalog = Catalog.getInstance();
        List<CatalogSchema> schemaList = catalog.getSchemas( this.stream.getTopic().databaseId, null );


        // check for existing namespace with DOCUMENT NamespaceType:
        //TODO: if wird nur gemacht, wenn Topic das erste mal Nachricht bekommt und noch kein storage zugewiesen bekommen hat.
        // DH: wenn CatalogObject oder storeID noch null ist, dann diese ganze Abfrage machen.
        if ( catalog.checkIfExistsSchema( this.stream.getTopic().databaseId, this.stream.getTopic().namespaceName ) ) {

            CatalogSchema schema = null;
            try {
                schema = catalog.getSchema( this.stream.getTopic().databaseId, this.stream.getTopic().namespaceName );
            } catch ( UnknownSchemaException e ) {
                log.error( "The catalog seems to be corrupt, as it was impossible to retrieve an existing namespace." );
                return 0;
            }

            assert schema != null;
            if ( schema.namespaceType == NamespaceType.DOCUMENT ) {
                this.stream.setTopic( this.stream.getTopic().setNamespaceId( schema.id ) );
                //check for collection with same name //TODO: maybe change the collection name, currently collection name is the topic
                List<CatalogCollection> collectionList = catalog.getCollections( schema.id, null );
                for ( CatalogCollection collection : collectionList ) {
                    if ( collection.name.equals( this.stream.getTopic().topicName ) ) {
                        if ( !collection.placements.contains( this.stream.getTopic().queryInterfaceId ) ) {
                            return collection.addPlacement( this.stream.getTopic().queryInterfaceId ).id;
                        } else {
                            return collection.id;
                        }
                    }
                }
                return createNewCollection();

            } else {
                this.stream.setNamespaceId( addNewNamespace() );
                return createNewCollection();
            }
        } else {
            this.stream.setNamespaceId( addNewNamespace() );
            return createNewCollection();
        }
    }


    private long addNewNamespace() {
        Catalog catalog = Catalog.getInstance();
        long namespaceId = catalog.addNamespace( this.stream.getTopic().namespaceName, this.stream.getTopic().databaseId, this.stream.getTopic().userId, NamespaceType.DOCUMENT );
        try {
            catalog.commit();
        } catch ( NoTablePrimaryKeyException e ) {
            log.error( "An error " );
        }
        return namespaceId;
    }


    private long createNewCollection() {
        Catalog catalog = Catalog.getInstance();

        //Catalog.PlacementType placementType = Catalog.PlacementType.AUTOMATIC;
        Transaction transaction = getTransaction();
        Statement statement = transaction.createStatement();

        try {
            List<DataStore> dataStores = new ArrayList<>();
            DdlManager.getInstance().createCollection(
                    this.stream.getTopic().namespaceId,
                    this.stream.getTopic().topicName,
                    true,   //only creates collection if it does not already exist.
                    dataStores.size() == 0 ? null : dataStores,
                    PlacementType.MANUAL,
                    statement );
            log.info( "Created Collection with name: {}", this.stream.getTopic().topicName );
            transaction.commit();
        } catch ( EntityAlreadyExistsException e ) {
            log.error( "The generation of the collection was not possible because there is a collaction already existing with this name." );
            return 0;
        } catch ( TransactionException e ) {
            log.error( "The commit after creating a new Collection could be completed!" );
            return 0;
        }
        //add placement
        List<CatalogCollection> collectionList = catalog.getCollections( this.stream.getTopic().namespaceId, null );
        for ( int i = 0; i < collectionList.size(); i++ ) {
            if ( collectionList.get( i ).name.equals( this.stream.getTopic().topicName ) ) {
                collectionList.set( i, collectionList.get( i ).addPlacement( this.stream.getTopic().queryInterfaceId ) );

                return collectionList.get( i ).id;
            }
        }
        return 0;
    }


    // added by Datomo
    public void insertDocument() {
        String collectionName = "users";
        Transaction transaction = getTransaction();
        Statement statement = transaction.createStatement();

        // Builder which allows to construct the algebra tree which is equivalent to query and is executed
        AlgBuilder builder = AlgBuilder.create( statement );

        // we insert document { age: 28, name: "David" } into the collection users
        BsonDocument document = new BsonDocument();
        document.put( "age", new BsonInt32( 28 ) );
        document.put( "name", new BsonString( "David" ) );

        AlgNode algNode = builder.docInsert( statement, collectionName, document ).build();

        // we can then wrap the tree in an AlgRoot and execute it
        AlgRoot root = AlgRoot.of( algNode, Kind.INSERT );
        // for inserts and all DML queries only a number is returned
        String res = executeAndTransformPolyAlg( root, statement, statement.getPrepareContext() );


    }


    // added by Datomo
    public void scanDocument() {
        String collectionName = "users";
        Transaction transaction = getTransaction();
        Statement statement = transaction.createStatement();

        // Builder which allows to construct the algebra tree which is equivalent to query and is executed
        AlgBuilder builder = AlgBuilder.create( statement );

        AlgNode algNode = builder.docScan( statement, collectionName ).build();

        // we can then wrap the tree in an AlgRoot and execute it
        AlgRoot root = AlgRoot.of( algNode, Kind.SELECT );
        String res = executeAndTransformPolyAlg( root, statement, statement.getPrepareContext() );

    }


    boolean saveContent() {
/**
 //TODO: save Message here -> Polyalgebra
 Transaction transaction = getTransaction();
 Statement statement = transaction.createStatement();
 AlgBuilder algBuilder = AlgBuilder.create( statement );
 JavaTypeFactory typeFactory = transaction.getTypeFactory();
 RexBuilder rexBuilder = new RexBuilder( typeFactory );

 PolyphenyDbCatalogReader catalogReader = statement.getTransaction().getCatalogReader();
 List<String> names = new ArrayList<>();
 //TODO: change naming maybe
 names.add( this.stream.topic );
 AlgOptTable table = catalogReader.getCollection( names );

 // Values
 AlgDataType tableRowType = table.getRowType(  );
 List<AlgDataTypeField> tableRows = tableRowType.getFieldList();

 AlgOptPlanner planner = statement.getQueryProcessor().getPlanner();
 AlgOptCluster cluster = AlgOptCluster.create( planner, rexBuilder );

 List<String> valueColumnNames = this.valuesColumnNames( insertValueRequest.values );
 List<RexNode> rexValues = this.valuesNode( statement, algBuilder, rexBuilder, insertValueRequest, tableRows, inputStreams ).get( 0 );
 algBuilder.push( LogicalValues.createOneRow( cluster ) );
 algBuilder.project( rexValues, valueColumnNames );

 // Table Modify
 AlgNode algNode = algBuilder.build();
 Modify modify = new LogicalModify(
 cluster,
 algNode.getTraitSet(),
 table,
 catalogReader,
 algNode,
 LogicalModify.Operation.INSERT,
 null,
 null,
 false
 );

 // Wrap {@link AlgNode} into a RelRoot
 final AlgDataType rowType = modify.getRowType();
 final List<Pair<Integer, String>> fields = Pair.zip( ImmutableIntList.identity( rowType.getFieldCount() ), rowType.getFieldNames() );
 final AlgCollation collation =
 algNode instanceof Sort
 ? ((Sort) algNode).collation
 : AlgCollations.EMPTY;
 AlgRoot root = new AlgRoot( modify, rowType, Kind.INSERT, fields, collation );
 log.debug( "AlgRoot was built." );

 Context ctx = statement.getPrepareContext();
 log.info( executeAndTransformPolyAlg( root, statement, ctx ) );
 **/
        return true;
    }


    private Transaction getTransaction() {
        try {
            return transactionManager.startTransaction( this.stream.getTopic().userId, this.stream.getTopic().databaseId, false, "MQTT Stream" );
        } catch ( UnknownUserException | UnknownDatabaseException | UnknownSchemaException | GenericCatalogException e ) {
            throw new RuntimeException( "Error while starting transaction", e );
        }
    }


    String executeAndTransformPolyAlg( AlgRoot algRoot, Statement statement, final Context ctx ) {
        //TODO: implement

        try {
            // Prepare
            PolyImplementation result = statement.getQueryProcessor().prepareQuery( algRoot, false );
            log.debug( "AlgRoot was prepared." );

            /*final Iterable<Object> iterable = result.enumerable( statement.getDataContext() );
            Iterator<Object> iterator = iterable.iterator();
            while ( iterator.hasNext() ) {
                iterator.next();
            }*/
            // todo transform into desired output format
            List<List<Object>> rows = result.getRows( statement, -1 );

            statement.getTransaction().commit();
            return rows.toString();
        } catch ( Throwable e ) {
            log.error( "Error during execution of REST query", e );
            try {
                statement.getTransaction().rollback();
            } catch ( TransactionException transactionException ) {
                log.error( "Could not rollback", e );
            }
            return null;
        }
        //Pair<String, Integer> result = restResult.getResult( ctx );

        //return result.left;
        //return null;
    }


    private static String registerTopicFolder( String topic ) {
        PolyphenyHomeDirManager homeDirManager = PolyphenyHomeDirManager.getInstance();

        String path = File.separator + "mqttStreamPlugin" + File.separator + topic.replace( "/", File.separator );
        ;

        File file = null;
        if ( !homeDirManager.checkIfExists( path ) ) {
            file = homeDirManager.registerNewFolder( path );
            log.info( "New Directory created!" );
        } else {
            //TODO: rmv log
            log.info( "Directory already exists" );
        }
        return path;
    }

}
