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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.category.PolyBlob;

@EqualsAndHashCode(callSuper = true)
@Value
public class PolyImage extends PolyBlob {

    public byte[] value;


    public PolyImage( byte[] value ) {
        super( PolyType.IMAGE );
        this.value = value;
    }


    public static PolyImage of( byte[] value ) {
        return new PolyImage( value );
    }


    public static PolyImage ofNullable( byte[] value ) {
        return of( value );
    }


    public static PolyImage ofNullable( @Nullable Byte[] value ) {
        return value == null ? new PolyImage( null ) : new PolyImage( ArrayUtils.toPrimitive( value ) );
    }

}
