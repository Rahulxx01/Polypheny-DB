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

package org.polypheny.db.protointerface.utils;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.linq4j.Ord;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.plan.AlgOptUtil;
import org.polypheny.db.processing.QueryProcessorHelpers;
import org.polypheny.db.protointerface.proto.ArrayMeta;
import org.polypheny.db.protointerface.proto.ColumnMeta;
import org.polypheny.db.protointerface.proto.FieldMeta;
import org.polypheny.db.protointerface.proto.Frame;
import org.polypheny.db.protointerface.proto.ProtoValueType;
import org.polypheny.db.protointerface.proto.Row;
import org.polypheny.db.protointerface.proto.StatementResult;
import org.polypheny.db.protointerface.proto.StatementStatus;
import org.polypheny.db.protointerface.proto.StructMeta;
import org.polypheny.db.protointerface.proto.TypeMeta;
import org.polypheny.db.protointerface.statements.ProtoInterfaceStatement;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyValue;

public class ProtoUtils {




    public static Row serializeToRow( List<PolyValue> row ) {
        return Row.newBuilder()
                .addAllValues( row.stream().map( PolyValueSerializer::serialize ).collect( Collectors.toList() ) )
                .build();
    }


    public static Frame buildFrame( List<List<PolyValue>> rows, List<ColumnMeta> metas ) {
        return Frame.newBuilder()
                .addAllColumnMeta( metas )
                .addAllRows( rows.stream().map( ProtoUtils::serializeToRow ).collect( Collectors.toList() ) )
                .build();
    }


    public static List<ColumnMeta> buildColumnMetasFromAvatica( List<ColumnMetaData> avaticaColumnMetas ) {
        return avaticaColumnMetas.stream().map( ProtoUtils::buildColumnMetaFromAvatica ).collect( Collectors.toList() );
    }


    public static ColumnMeta buildColumnMetaFromAvatica( ColumnMetaData avaticaColumnMeta ) {
        throw new NotImplementedException("DONT USE THIS!");
    }





    public static StatementStatus createStatus( ProtoInterfaceStatement protoInterfaceStatement ) {
        return StatementStatus.newBuilder()
                .setStatementId( protoInterfaceStatement.getStatementId() )
                .build();
    }


    public static StatementStatus createStatus( ProtoInterfaceStatement protoInterfaceStatement, StatementResult result ) {
        return StatementStatus.newBuilder()
                .setStatementId( protoInterfaceStatement.getStatementId() )
                .setResult( result )
                .build();
    }

}
