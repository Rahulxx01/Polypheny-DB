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

package org.polypheny.db.catalog.entity.logical;

import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import io.activej.serializer.annotations.SerializeNullable;
import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.LogicalDefaultValue;
import org.polypheny.db.catalog.entity.PolyObject;
import org.polypheny.db.catalog.logistic.Collation;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.numerical.PolyInteger;


@EqualsAndHashCode()
@Value
@SuperBuilder(toBuilder = true)
@NonFinal
public class LogicalColumn implements PolyObject, Comparable<LogicalColumn> {

    @Serial
    private static final long serialVersionUID = -4792846455300897399L;

    @Serialize
    public long id;

    @Serialize
    public String name;

    @Serialize
    public long tableId;

    @Serialize
    public long namespaceId;

    @Serialize
    public int position;

    @Serialize
    public PolyType type;

    @Serialize
    public @SerializeNullable PolyType collectionsType;

    @Serialize
    //@SerializeVarLength // fixes bug with 255 length
    public @SerializeNullable Integer length; // JDBC length or precision depending on type

    @Serialize
    public @SerializeNullable Integer scale; // decimal digits

    @Serialize
    public @SerializeNullable Integer dimension;

    @Serialize
    public @SerializeNullable Integer cardinality;

    @Serialize
    public boolean nullable;

    @Serialize
    public @SerializeNullable Collation collation;

    @Serialize
    @SerializeNullable
    public LogicalDefaultValue defaultValue;

    public DataModel dataModel = DataModel.RELATIONAL;


    public LogicalColumn(
            @Deserialize("id") final long id,
            @Deserialize("name") @NonNull final String name,
            @Deserialize("tableId") final long tableId,
            @Deserialize("namespaceId") final long namespaceId,
            @Deserialize("position") final int position,
            @Deserialize("type") @NonNull final PolyType type,
            @Deserialize("collectionsType") final PolyType collectionsType,
            @Deserialize("length") final Integer length,
            @Deserialize("scale") final Integer scale,
            @Deserialize("dimension") final Integer dimension,
            @Deserialize("cardinality") final Integer cardinality,
            @Deserialize("nullable") final boolean nullable,
            @Deserialize("collation") final Collation collation,
            @Deserialize("defaultValue") final LogicalDefaultValue defaultValue ) {
        this.id = id;
        this.name = name;
        this.tableId = tableId;
        this.namespaceId = namespaceId;
        this.position = position;
        this.type = type;
        this.collectionsType = collectionsType;
        this.length = length;
        this.scale = scale;
        this.dimension = dimension;
        this.cardinality = cardinality;
        this.nullable = nullable;
        this.collation = collation;
        this.defaultValue = defaultValue;
    }


    public AlgDataType getAlgDataType( final AlgDataTypeFactory typeFactory ) {
        AlgDataType elementType;
        if ( this.length != null && this.scale != null && this.type.allowsPrecScale( true, true ) ) {
            elementType = typeFactory.createPolyType( this.type, this.length, this.scale );
        } else if ( this.length != null && this.type.allowsPrecNoScale() ) {
            elementType = typeFactory.createPolyType( this.type, this.length );
        } else {
            assert this.type.allowsNoPrecNoScale();
            elementType = typeFactory.createPolyType( this.type );
        }

        if ( collectionsType == PolyType.ARRAY ) {
            elementType = typeFactory.createArrayType( elementType, cardinality != null ? cardinality : -1, dimension != null ? dimension : -1 );
        } else if ( collectionsType == PolyType.MAP ) {
            elementType = typeFactory.createMapType( typeFactory.createPolyType( PolyType.ANY ), elementType );
        }

        return typeFactory.createTypeWithNullability( elementType, nullable );
    }


    public String getNamespaceName() {
        return Catalog.snapshot().getNamespace( namespaceId ).orElseThrow().name;
    }


    public String getTableName() {
        return Catalog.snapshot().rel().getTable( tableId ).orElseThrow().name;
    }

    public String getDatabaseName() {
        return Catalog.DATABASE_NAME;
    }

    @Override
    public PolyValue[] getParameterArray() {
        return new PolyValue[]{
                PolyString.of( Catalog.DATABASE_NAME ),
                PolyString.of( getNamespaceName() ),
                PolyString.of( getTableName() ),
                PolyString.of( name ),
                PolyInteger.of( type.getJdbcOrdinal() ),
                PolyString.of( type.name() ),
                PolyInteger.of( length ),
                null,
                PolyInteger.of( scale ),
                null,
                PolyInteger.of( nullable ? 1 : 0 ),
                PolyString.of( "" ),
                PolyString.of( defaultValue == null ? null : defaultValue.value.toJson() ),
                null,
                null,
                null,
                PolyInteger.of( position ),
                PolyString.of( nullable ? "YES" : "NO" ),
                PolyString.of( PolyObject.getEnumNameOrNull( collation ) ) };
    }


    @Override
    public int compareTo( LogicalColumn o ) {
        int comp = (int) (this.namespaceId - o.namespaceId);
        if ( comp == 0 ) {
            comp = (int) (this.tableId - o.tableId);
            if ( comp == 0 ) {
                return (int) (this.id - o.id);
            } else {
                return comp;
            }

        } else {
            return comp;
        }

    }


    @RequiredArgsConstructor
    public static class PrimitiveCatalogColumn {

        public final String tableCat;
        public final String tableSchem;
        public final String tableName;
        public final String columnName;
        public final int dataType;
        public final String typeName;
        public final Integer columnSize; // precision or length
        public final Integer bufferLength; // always null
        public final Integer decimalDigits; // scale
        public final Integer numPrecRadix;
        public final int nullable;
        public final String remarks;
        public final String columnDef;
        public final Integer sqlDataType; // always null
        public final Integer sqlDatetimeSub; // always null
        public final Integer charOctetLength; // always null
        public final int ordinalPosition; // position
        public final String isNullable;

        public final String collation;

    }


}
