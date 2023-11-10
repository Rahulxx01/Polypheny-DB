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

package org.polypheny.db.avatica;


import java.util.Objects;
import lombok.Getter;
import org.apache.calcite.avatica.ConnectionPropertiesImpl;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.avatica.Meta.ConnectionHandle;
import org.apache.calcite.avatica.Meta.ConnectionProperties;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.LogicalDatabase;
import org.polypheny.db.catalog.entity.LogicalUser;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.transaction.PUID.ConnectionId;
import org.polypheny.db.transaction.PUID.UserId;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionManager;


/**
 *
 */
public class PolyphenyDbConnectionHandle {

    private final Meta.ConnectionHandle handle;

    private final UserId userId;

    @Getter
    private final LogicalUser user;
    private final LogicalDatabase database;
    private final LogicalNamespace namespace;

    @Getter
    private final ConnectionId connectionId;
    private Transaction currentTransaction;
    private PolyphenyDbResultSet currentOpenResultSet;

    private final TransactionManager transactionManager;

    private final ConnectionProperties connectionProperties = new ConnectionPropertiesImpl( true, false, java.sql.Connection.TRANSACTION_SERIALIZABLE, Catalog.DATABASE_NAME, Catalog.defaultNamespaceName );


    public PolyphenyDbConnectionHandle( final Meta.ConnectionHandle handle, final LogicalUser logicalUser, final ConnectionId connectionId, final LogicalDatabase database, final LogicalNamespace namespace, final TransactionManager transactionManager ) {
        this.handle = handle;

        this.userId = UserId.fromString( logicalUser.name ); // TODO: refactor CatalogUser
        this.user = logicalUser;
        this.connectionId = connectionId;
        this.database = database;
        this.namespace = namespace;
        this.transactionManager = transactionManager;
    }


    public PolyphenyDbConnectionHandle( final ConnectionHandle handle, final LogicalUser logicalUser, final String connectionId, final LogicalDatabase database, final LogicalNamespace namespace, final TransactionManager transactionManager ) {
        this.handle = handle;

        this.userId = UserId.fromString( logicalUser.name ); // TODO: refactor CatalogUser
        this.user = logicalUser;
        this.connectionId = ConnectionId.fromString( connectionId );
        this.database = database;
        this.namespace = namespace;
        this.transactionManager = transactionManager;
    }


    public Transaction getCurrentTransaction() {
        synchronized ( this ) {
            return currentTransaction;
        }
    }


    public Transaction endCurrentTransaction() {
        synchronized ( this ) {
            Transaction endedTransaction = currentTransaction;
            currentTransaction = null;
            return endedTransaction;
        }
    }


    public Transaction getCurrentOrCreateNewTransaction() {
        synchronized ( this ) {
            if ( currentTransaction == null || !currentTransaction.isActive() ) {
                currentTransaction = transactionManager.startTransaction( user.id, namespace.id, false, "AVATICA Interface" );
            }
            return currentTransaction;
        }
    }


    public void setCurrentOpenResultSet( PolyphenyDbResultSet resultSet ) {
        this.currentOpenResultSet = resultSet;
    }


    public ConnectionProperties mergeConnectionProperties( final ConnectionProperties connectionProperties ) {
        return this.connectionProperties.merge( connectionProperties );
    }


    public boolean isAutoCommit() {
        return this.connectionProperties.isAutoCommit();
    }


    public Meta.ConnectionHandle getHandle() {
        return this.handle;
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }
        PolyphenyDbConnectionHandle that = (PolyphenyDbConnectionHandle) o;
        return Objects.equals( connectionId, that.connectionId );
    }


    @Override
    public int hashCode() {
        return Objects.hash( connectionId );
    }

}
