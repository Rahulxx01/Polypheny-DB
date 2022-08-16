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

package org.polypheny.db.adaptimizer.rnddata;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.PolyResult;
import org.polypheny.db.adaptimizer.AdaptiveOptimizerImpl;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.catalog.Catalog.SchemaType;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.languages.QueryParameters;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.processing.Processor;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Pair;

@Slf4j
@Getter(AccessLevel.PRIVATE)
public class DataGenerator {
    private final Catalog catalog;
    private final DataRecordGenerator records;
    private final ImmutableMap<Long, Integer> sizes;
    private final int buffer;

    /**
     * Creates a Data Generator for a list of tables.
     * @param records  {@link DataRecordGenerator}
     * @param buffer    how many records are inserted each iteration.
     */
    public DataGenerator( DataRecordGenerator records, int buffer ) {
        this.catalog = AdaptiveOptimizerImpl.getCatalog();
        this.sizes = records.getTSize();
        this.records = records;
        this.buffer = buffer;
    }

    /**
     * Generates Data and inserts it into the store.
     */
    public void generateData() {
        getSizes().keySet().stream().map( getCatalog()::getTable ).forEach( this::generateDataForTable );
    }

    /**
     * Generates and inserts data into a given table.
     */
    private void generateDataForTable( CatalogTable table ) {
        Transaction transaction = getTransaction();

        for ( int r = 0; r < getSizes().get( table.id ); r += getBuffer() ) {
            List<List<Object>> data = generateSubset( table.id, r );
            String query = getInsertQuery( table, data );
            executeSqlInsert( transaction.createStatement(), query );
        }
        commitTransaction( transaction );
        getRecords().resetRnd();
    }

    private List<List<Object>> generateSubset( long t, int r ) {
        return Stream.generate( () -> getRecords().generateFor( t ) ).limit( currLim( t, r ) ).collect( Collectors.toList());
    }

    private int currLim( long t, int r ) {
        return ( r + getBuffer() > getSizes().get( t ) ) ? r + getBuffer() - getSizes().get( t ) : getBuffer();
    }

    private String getInsertQuery( CatalogTable table, List<List<Object>> data ) {
        return getInsertQuery( table.getSchemaName(), table.name, getRecords().getColumns( table.id ), data );
    }

    private String getInsertQuery( String schema, String table, List<CatalogColumn> columns, List<List<Object>> data ) {
        StringJoiner sqlInsert = new StringJoiner( " ", "", "" );
        setSqlInsertPrefix( sqlInsert, schema, table );
        setSqlInsertColumns( sqlInsert, columns );
        setSqlInsertBulk( sqlInsert, columns, data );
        return sqlInsert.toString();
    }

    private void setSqlInsertPrefix( StringJoiner sqlInsert, String schema, String table ) {
        sqlInsert.add( "insert" ).add( "into" ).add( schema + "." + table );
    }

    private void setSqlInsertColumns( StringJoiner sqlInsert, List<CatalogColumn> columns ) {
        StringJoiner colSj = new StringJoiner( ",", "(", ")" );
        columns.stream().map( column -> column.name ).forEach( colSj::add );
        sqlInsert.add( colSj.toString() );
    }

    private void setSqlInsertBulk( StringJoiner sqlInsert, List<CatalogColumn> columns, List<List<Object>> data ) {
        StringJoiner bulkSj = new StringJoiner( ",", "", "" );
        StringJoiner valueSj;
        for ( List<Object> record : data ) {
            valueSj = new StringJoiner( ",", "(", ")" );
            for ( int i = 0; i < record.size(); i++ ) {
                setSqlInsertValue( valueSj, columns.get( i ).type, record.get( i ) );
            }
            bulkSj.add( valueSj.toString() );
        }
        sqlInsert.add( "values" ).add( bulkSj.toString() );
    }

    private void setSqlInsertValue( StringJoiner valueSj, PolyType type, Object obj ) {
            switch ( type ) {
                case TIMESTAMP:
                    valueSj.add( "timestamp '" + obj + "'" );
                    break;
                case DATE:
                    valueSj.add( "date '" + obj + "'" );
                    break;
                case TIME:
                    valueSj.add( "time '" + obj + "'" );
                    break;
                case CHAR:
                case VARCHAR:
                    valueSj.add( "'" + obj + "'" );
                    break;
                default:
                    valueSj.add( String.valueOf( obj ) );
                    break;
        }
    }

    private Transaction getTransaction() {
        Transaction transaction;
        try {
            transaction = AdaptiveOptimizerImpl.getTransactionManager().startTransaction(
                    getCatalog().getUser( Catalog.defaultUserId ).name,
                    getCatalog().getDatabase( Catalog.defaultDatabaseId ).name,
                    false,
                    "DataGenerator"
            );
        } catch ( UnknownDatabaseException | GenericCatalogException | UnknownUserException | UnknownSchemaException e ) {
            e.printStackTrace();
            throw new RndDataException( "Could not start transaction", e );
        }
        return transaction;
    }


    private void commitTransaction(Transaction transaction) {
        try {
            transaction.commit();
        } catch ( TransactionException e ) {
            e.printStackTrace();
            log.error( "Could not commit.", e );
            try {
                transaction.rollback();
            } catch ( TransactionException e2 ) {
                log.error( "Caught error while rolling back transaction", e2 );
            }
            throw new RndDataException( "Could not insert data into table", e );
        }
    }


    private void executeSqlInsert(Statement statement, String insertQuery ) {
        Processor sqlProcessor = statement.getTransaction().getProcessor( QueryLanguage.SQL );
        Pair<Node, AlgDataType> validated = sqlProcessor.validate( statement.getTransaction(), sqlProcessor.parse( insertQuery ), false );
        AlgRoot logicalRoot = sqlProcessor.translate( statement, validated.left, new QueryParameters( insertQuery, SchemaType.RELATIONAL ) );
        PolyResult polyResult = statement.getQueryProcessor().prepareQuery( logicalRoot, false );
        try {
            polyResult.getRowsChanged( statement );
        } catch ( Exception e ) {
            throw new RndDataException( "Could not execute insert query", e );
        }
    }

}
