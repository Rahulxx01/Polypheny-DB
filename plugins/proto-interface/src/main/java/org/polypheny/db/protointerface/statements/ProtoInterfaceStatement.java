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

package org.polypheny.db.protointerface.statements;

import java.util.Iterator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.commons.lang3.time.StopWatch;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.protointerface.ProtoInterfaceClient;

@Slf4j
public abstract class ProtoInterfaceStatement {

    protected final long creationDate;
    @Getter
    protected final int statementId;
    protected final ProtoInterfaceClient protoInterfaceClient;
    protected final StopWatch executionStopWatch;
    protected final QueryLanguage queryLanguage;
    protected final String query;
    @Setter
    protected int maxRowCount;

    protected Iterator<Object> resultIterator;


    public ProtoInterfaceStatement( int statementId, ProtoInterfaceClient protoInterfaceClient, QueryLanguage queryLanguage, String query ) {
        if ( query == null ) {
            throw new NullPointerException( "statement must not be null." );
        }
        if ( protoInterfaceClient == null ) {
            throw new NullPointerException( "proto interface client must not be null." );
        }
        if ( queryLanguage == null ) {
            throw new NullPointerException( "query language must not be null." );
        }
        this.statementId = statementId;
        this.protoInterfaceClient = protoInterfaceClient;
        this.queryLanguage = queryLanguage;
        this.query = query;
        this.creationDate = System.currentTimeMillis();
        this.executionStopWatch = new StopWatch();
        this.maxRowCount = DEFAULT_FETCH_ROW_COUNT;
    }


    public abstract StatementResult execute() throws Exception;

    public abstract Frame fetch( long offset );


    public Frame fetchFirst() {
        return fetch( 0 );
    }


    protected Iterator<Object> getOrCreateIterator() {
        if ( resultIterator != null ) {
            return resultIterator;
        }
        Statement statement = currentImplementation.getStatement();
        final Enumerable<Object> enumerable = currentImplementation.getBindable().bind( statement.getDataContext() );
        resultIterator = enumerable.iterator();
        return resultIterator;
    }


    public long getMillisSinceCreation() {
        return System.currentTimeMillis() - creationDate;
    }


    protected void commitElseRollback() {
        try {
            protoInterfaceClient.commitCurrentTransaction();
        } catch ( Exception e ) {
            protoInterfaceClient.rollbackCurrentTransaction();
        }
    }


    protected void startOrResumeStopwatch() {
        if ( executionStopWatch.isSuspended() ) {
            executionStopWatch.resume();
            return;
        }
        if ( executionStopWatch.isStopped() ) {
            executionStopWatch.start();
        }
    }

}
