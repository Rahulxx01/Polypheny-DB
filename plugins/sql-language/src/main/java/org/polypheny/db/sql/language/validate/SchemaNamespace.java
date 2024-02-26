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

package org.polypheny.db.sql.language.validate;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.sql.language.SqlNode;


/**
 * Namespace based on a schema.
 *
 * The visible names are tables and sub-schemas.
 */
class SchemaNamespace extends AbstractNamespace {

    /**
     * The path of this schema.
     */
    private final ImmutableList<String> names;


    /**
     * Creates a SchemaNamespace.
     */
    SchemaNamespace( SqlValidatorImpl validator, ImmutableList<String> names ) {
        super( validator, null );
        this.names = Objects.requireNonNull( names );
    }


    @Override
    protected AlgDataType validateImpl( AlgDataType targetRowType ) {
        final AlgDataTypeFactory.Builder builder = validator.getTypeFactory().builder();
        /*for ( LogicalTable table : validator.snapshot.getLogicalTables( List.of( names.get( 0 ), names.get( 1 ) ) ) ) {
            //final List<String> names1 = moniker.getFullyQualifiedNames();
            builder.add( Util.last( names1 ), null, table.getRowType() );
        }*/// todo dl
        return targetRowType; //builder.build();
    }


    @Override
    public SqlNode getNode() {
        return null;
    }

}
