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

package org.polypheny.db.mql.mql2alg;

import org.polypheny.db.catalog.MockCatalog;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.entity.LogicalNamespace;
import org.polypheny.db.catalog.logistic.NamespaceType;


public class MqlMockCatalog extends MockCatalog {

    @Override
    public LogicalNamespace getNamespace( long id ) {
        return new LogicalNamespace( 1, "private", 0, 0, "tester", NamespaceType.DOCUMENT, true );
    }


    @Override
    public CatalogUser getUser( long id ) {
        return new CatalogUser( 0, "name", "name" );
    }

}
