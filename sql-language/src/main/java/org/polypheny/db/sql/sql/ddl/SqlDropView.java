/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.sql.sql.ddl;


import static org.polypheny.db.util.Static.RESOURCE;

import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.catalog.Catalog.EntityType;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.ddl.exception.DdlOnSourceException;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.languages.QueryParameters;
import org.polypheny.db.prepare.Context;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.sql.sql.SqlIdentifier;
import org.polypheny.db.sql.sql.SqlOperator;
import org.polypheny.db.sql.sql.SqlSpecialOperator;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.util.CoreUtil;


/**
 * Parse tree for {@code DROP VIEW} statement.
 */
public class SqlDropView extends SqlDropObject {

    private static final SqlOperator OPERATOR = new SqlSpecialOperator( "DROP VIEW", Kind.DROP_VIEW );


    /**
     * Creates a SqlDropView.
     */
    SqlDropView( ParserPos pos, boolean ifExists, SqlIdentifier name ) {
        super( OPERATOR, pos, ifExists, name );
    }


    @Override
    public void execute( Context context, Statement statement, QueryParameters parameters ) {
        final CatalogEntity catalogEntity;

        try {
            catalogEntity = getCatalogTable( context, name );
        } catch ( PolyphenyDbContextException e ) {
            if ( ifExists ) {
                // It is ok that there is no database / schema / table with this name because "IF EXISTS" was specified
                return;
            } else {
                throw e;
            }
        }

        if ( catalogEntity.entityType != EntityType.VIEW ) {
            throw new RuntimeException( "Not Possible to use DROP VIEW because " + catalogEntity.name + " is not a View." );
        }

        try {
            DdlManager.getInstance().dropView( catalogEntity, statement );
        } catch ( DdlOnSourceException e ) {
            throw CoreUtil.newContextException( name.getPos(), RESOURCE.ddlOnSourceTable() );
        }

    }

}

