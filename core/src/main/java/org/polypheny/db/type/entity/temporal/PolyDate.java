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

package org.polypheny.db.type.entity.temporal;

import com.fasterxml.jackson.core.JsonToken;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.functions.Functions;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyLong;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.category.PolyTemporal;

@Getter
@Value
@EqualsAndHashCode(callSuper = true)
public class PolyDate extends PolyTemporal {

    public static final DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );

    public Long milliSinceEpoch;


    public PolyDate( long milliSinceEpoch ) {
        super( PolyType.DATE );
        this.milliSinceEpoch = milliSinceEpoch;
    }


    public static PolyDate of( Number number ) {
        return new PolyDate( number.longValue() );
    }


    public static PolyDate ofNullable( Number number ) {
        return number == null ? null : of( number );
    }


    public static PolyDate ofNullable( java.sql.Date date ) {
        return PolyDate.of( date );
    }


    public static PolyValue ofDays( int days ) {
        return new PolyDate( days * 24L * 60 * 60 * 1000 );
    }


    public Date asDefaultDate() {
        return new Date( milliSinceEpoch );
    }


    public java.sql.Date asSqlDate() {
        return new java.sql.Date( milliSinceEpoch );
    }


    public static PolyDate of( Date date ) {
        return new PolyDate( Functions.dateToLong( date ) );
    }


    @Override
    public String toJson() {
        return milliSinceEpoch == null ? JsonToken.VALUE_NULL.asString() : dateFormat.format( new Date( milliSinceEpoch ) );
    }


    @Override
    public int compareTo( @NotNull PolyValue o ) {
        if ( !isDate() ) {
            return -1;
        }

        return Long.compare( milliSinceEpoch, o.asDate().milliSinceEpoch );
    }


    @Override
    public Expression asExpression() {
        return Expressions.new_( PolyLong.class, Expressions.constant( milliSinceEpoch ) );
    }


    @Override
    public @Nullable Long deriveByteSize() {
        return null;
    }


    @Override
    public PolySerializable copy() {
        return PolySerializable.deserialize( serialize(), PolyDate.class );
    }

}
