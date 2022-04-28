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

package org.polypheny.db.languages.mql2alg;

import org.polypheny.db.catalog.MockCatalog;
import org.polypheny.db.catalog.entity.CatalogProcedure;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.exceptions.UnknownProcedureException;

import java.util.Collections;
import java.util.List;


public class MqlMockCatalog extends MockCatalog {

    @Override
    public CatalogSchema getSchema( long schemaId ) {
        return new CatalogSchema( 1, "private", 0, 0, "tester", SchemaType.DOCUMENT );
    }


    @Override
    public CatalogUser getUser( int userId ) {
        return new CatalogUser( 0, "name", "name", 1 );
    }

    @Override
    public void addProcedure(Long schemaId, String procedureName, Long databaseId, String query, String... arguments) {

    }

    @Override
    public List<CatalogProcedure> getProcedures(Long schemaId) {
        return Collections.emptyList();
    }

    @Override
    public CatalogProcedure getProcedure(long databaseId, long schemaId, String tableName) throws UnknownProcedureException {
        return new CatalogProcedure(1L, "myProcedure", 2L, 3L, "");
    }


}
