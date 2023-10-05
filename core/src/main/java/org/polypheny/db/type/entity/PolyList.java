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

package org.polypheny.db.type.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.activej.serializer.BinaryInput;
import io.activej.serializer.BinaryOutput;
import io.activej.serializer.BinarySerializer;
import io.activej.serializer.CompatibilityLevel;
import io.activej.serializer.CorruptedDataException;
import io.activej.serializer.SimpleSerializerDef;
import io.activej.serializer.annotations.Deserialize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Delegate;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Pair;

@EqualsAndHashCode(callSuper = true)
@Value(staticConstructor = "copyOf")
public class PolyList<E extends PolyValue> extends PolyValue implements List<E> {


    @Delegate
    @JsonProperty
    @JsonSerialize
    public List<E> value;


    public PolyList( @JsonProperty @Deserialize("value") List<E> value ) {
        super( PolyType.ARRAY );
        this.value = new ArrayList<>( value );
    }


    @SafeVarargs
    public PolyList( E... value ) {
        this( Arrays.asList( value ) );
    }


    public static <E extends PolyValue> PolyList<E> of( Collection<E> value ) {
        return new PolyList<>( new ArrayList<>( value ) );
    }


    @SuppressWarnings("unused")
    public static <E extends PolyValue> PolyList<E> ofNullable( Collection<E> value ) {
        return value == null ? null : new PolyList<>( new ArrayList<>( value ) );
    }


    @SafeVarargs
    public static <E extends PolyValue> PolyList<E> of( E... values ) {
        return new PolyList<>( values );
    }


    /**
     * Required due to limitation of call, where interfaces lead to errors.
     */
    @SuppressWarnings("unused")
    @SafeVarargs
    public static <E extends PolyValue> PolyList<E> ofExpression( E... values ) {
        return new PolyList<>( values );
    }


    @Override
    public Expression asExpression() {
        return Expressions.call( PolyList.class, "ofExpression", value.stream().map( e -> e == null ? Expressions.constant( null ) : e.asExpression() ).collect( Collectors.toList() ) );
    }


    @Override
    public int compareTo( @NotNull PolyValue o ) {
        if ( !isSameType( o ) ) {
            return -1;
        }

        if ( value.size() != o.asList().value.size() ) {
            return value.size() - o.asList().value.size();
        }

        for ( Pair<E, ?> pair : Pair.zip( value, o.asList().value ) ) {
            if ( pair.left.compareTo( (PolyValue) pair.right ) != 0 ) {
                return pair.left.compareTo( (PolyValue) pair.right );
            }
        }

        return 0;
    }


    @Override
    public PolySerializable copy() {
        return null;
    }


    @Override
    public @Nullable Long deriveByteSize() {
        return null;
    }


    public static class PolyListSerializerDef extends SimpleSerializerDef<PolyList<?>> {

        @Override
        protected BinarySerializer<PolyList<?>> createSerializer( int version, CompatibilityLevel compatibilityLevel ) {
            return new BinarySerializer<>() {
                @Override
                public void encode( BinaryOutput out, PolyList<?> item ) {
                    out.writeLong( item.size() );
                    for ( PolyValue entry : item ) {
                        out.writeUTF8( PolySerializable.serialize( serializer, entry ) );
                    }
                }


                @Override
                public PolyList<?> decode( BinaryInput in ) throws CorruptedDataException {
                    List<PolyValue> list = new ArrayList<>();
                    long size = in.readLong();
                    for ( long i = 0; i < size; i++ ) {
                        list.add( PolySerializable.deserialize( in.readUTF8(), serializer ) );
                    }
                    return PolyList.of( list );
                }
            };
        }

    }


}
