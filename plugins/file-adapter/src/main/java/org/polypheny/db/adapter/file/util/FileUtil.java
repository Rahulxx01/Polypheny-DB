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

package org.polypheny.db.adapter.file.util;

import org.polypheny.db.type.entity.PolyValue;

public class FileUtil {

    public static Object toObject( PolyValue value ) {
        return value == null ? null : value.toJson();
    }


    public static PolyValue fromValue( PolyValue value ) {
        if ( value == null ) {
            return null;
        }
        return value;
        /*return switch ( value.type ) {
            case INTEGER, TINYINT, SMALLINT -> value.asNumber().IntValue();
            case REAL, FLOAT -> value.asNumber().FloatValue();
            case VARCHAR, CHAR -> value.asString().value;
            case BIGINT, DECIMAL -> value.asNumber().BigDecimalValue();
            case BINARY, VARBINARY -> value.asBinary().value.getBytes();
            case DOUBLE -> value.asNumber().DoubleValue();
            case BOOLEAN -> value.asBoolean().value;
            case DATE -> value.asDate().getDaysSinceEpoch();
            case TIME -> value.asTime().ofDay;
            case TIMESTAMP -> value.asTimestamp().millisSinceEpoch;
            case ARRAY -> value.asList().toJson();
            default -> throw new NotImplementedException();
        };*/
    }

}
