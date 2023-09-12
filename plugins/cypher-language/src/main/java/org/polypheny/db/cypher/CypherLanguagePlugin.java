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

package org.polypheny.db.cypher;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.websocket.api.Session;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.cypher.parser.CypherParserImpl;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.languages.LanguageManager;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.plugins.PluginContext;
import org.polypheny.db.plugins.PolyPlugin;
import org.polypheny.db.plugins.PolyPluginManager;
import org.polypheny.db.processing.ExtendedQueryParameters;
import org.polypheny.db.processing.Processor;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionManager;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.webui.Crud;
import org.polypheny.db.webui.crud.LanguageCrud;
import org.polypheny.db.webui.models.requests.QueryRequest;
import org.polypheny.db.webui.models.results.GraphResult;
import org.polypheny.db.webui.models.results.Result;

public class CypherLanguagePlugin extends PolyPlugin {


    public static final String NAME = "cypher";


    /**
     * Constructor to be used by plugin manager for plugin instantiation.
     * Your plugins have to provide constructor with this exact signature to be successfully loaded by manager.
     */
    public CypherLanguagePlugin( PluginContext context ) {
        super( context );
    }


    @Override
    public void start() {
        PolyPluginManager.AFTER_INIT.add( () -> LanguageCrud.crud.languageCrud.addLanguage( NAME, CypherLanguagePlugin::anyCypherQuery ) );
        LanguageManager.getINSTANCE().addQueryLanguage( NamespaceType.GRAPH, NAME, List.of( NAME, "opencypher" ), CypherParserImpl.FACTORY, CypherProcessorImpl::new, null );

        if ( !CypherRegisterer.isInit() ) {
            CypherRegisterer.registerOperators();
        }
    }


    @Override
    public void stop() {
        LanguageCrud.crud.languageCrud.removeLanguage( NAME );
        LanguageManager.removeQueryLanguage( NAME );

        if ( CypherRegisterer.isInit() ) {
            CypherRegisterer.removeOperators();
        }
    }


    public static List<Result<?, ?>> anyCypherQuery(
            Session session,
            QueryRequest request,
            TransactionManager transactionManager,
            long userId,
            long namespaceId,
            Crud crud ) {

        String query = request.query;

        Transaction transaction = Crud.getTransaction( request.analyze, request.cache, transactionManager, userId, namespaceId, "HTTP Interface Cypher" );
        Processor cypherProcessor = transaction.getProcessor( QueryLanguage.from( NAME ) );

        List<Result<?, ?>> results = new ArrayList<>();

        InformationManager queryAnalyzer = null;
        long executionTime = 0;
        try {
            if ( request.analyze ) {
                transaction.getQueryAnalyzer().setSession( session );
            }

            queryAnalyzer = LanguageCrud.attachAnalyzerIfSpecified( request, crud, transaction );

            executionTime = System.nanoTime();

            Statement statement = transaction.createStatement();
            ExtendedQueryParameters parameters = new ExtendedQueryParameters( query, NamespaceType.GRAPH, request.namespaceId );

            if ( transaction.isAnalyze() ) {
                statement.getOverviewDuration().start( "Parsing" );
            }
            List<? extends Node> statements = cypherProcessor.parse( query );
            if ( transaction.isAnalyze() ) {
                statement.getOverviewDuration().stop( "Parsing" );
            }

            int i = 0;
            List<String> splits = List.of( query.split( ";" ) );
            assert statements.size() <= splits.size();
            for ( Node node : statements ) {
                CypherStatement stmt = (CypherStatement) node;

                if ( stmt.isDDL() ) {
                    cypherProcessor.prepareDdl( statement, node, parameters );
                    GraphResult result = GraphResult
                            .builder()
                            .affectedTuples( 1 )
                            .query( splits.get( i ) )
                            .namespaceId( request.namespaceId )
                            .xid( transaction.getXid().toString() )
                            .build();
                    results.add( result );
                } else {
                    if ( transaction.isAnalyze() ) {
                        statement.getOverviewDuration().start( "Translation" );
                    }
                    AlgRoot logicalRoot = cypherProcessor.translate( statement, stmt, parameters );
                    if ( transaction.isAnalyze() ) {
                        statement.getOverviewDuration().stop( "Translation" );
                    }

                    // Prepare
                    PolyImplementation<PolyValue> polyImplementation = statement.getQueryProcessor().prepareQuery( logicalRoot, true );

                    if ( transaction.isAnalyze() ) {
                        statement.getOverviewDuration().start( "Execution" );
                    }
                    results.add( LanguageCrud.getResult( QueryLanguage.from( NAME ), statement, request, query, polyImplementation, transaction, query.toLowerCase().contains( " limit " ) ) );
                    if ( transaction.isAnalyze() ) {
                        statement.getOverviewDuration().stop( "Execution" );
                    }
                }
                i++;
            }


        } catch ( Throwable t ) {
            LanguageCrud.printLog( t, request );
            LanguageCrud.attachError( transaction, results, query, t );
        }

        LanguageCrud.commitAndFinish( transaction, queryAnalyzer, results, executionTime );

        return results;
    }

}
