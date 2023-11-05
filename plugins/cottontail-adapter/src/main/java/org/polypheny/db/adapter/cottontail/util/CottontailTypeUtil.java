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

package org.polypheny.db.adapter.cottontail.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.ConstantExpression;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.Types;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.language.fun.SqlArrayValueConstructor;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyDouble;
import org.polypheny.db.type.entity.PolyList;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.util.BuiltInMethod;
import org.vitrivr.cottontail.grpc.CottontailGrpc;
import org.vitrivr.cottontail.grpc.CottontailGrpc.BoolVector;
import org.vitrivr.cottontail.grpc.CottontailGrpc.ColumnName;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Date;
import org.vitrivr.cottontail.grpc.CottontailGrpc.DoubleVector;
import org.vitrivr.cottontail.grpc.CottontailGrpc.EntityName;
import org.vitrivr.cottontail.grpc.CottontailGrpc.FloatVector;
import org.vitrivr.cottontail.grpc.CottontailGrpc.From;
import org.vitrivr.cottontail.grpc.CottontailGrpc.IntVector;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Literal;
import org.vitrivr.cottontail.grpc.CottontailGrpc.LongVector;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Projection;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Projection.ProjectionElement;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Scan;
import org.vitrivr.cottontail.grpc.CottontailGrpc.SchemaName;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Type;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Vector;


@Slf4j
public class CottontailTypeUtil {

    public static final Method COTTONTAIL_SIMPLE_CONSTANT_TO_DATA_METHOD = Types.lookupMethod(
            CottontailTypeUtil.class,
            "toData",
            Object.class, PolyType.class, PolyType.class );

    public static final Method COTTONTAIL_KNN_BUILDER_METHOD = Types.lookupMethod(
            Linq4JFixer.class,
            "generateKnn",
            String.class, Vector.class, String.class, String.class );


    /**
     * Maps the given map of attributes and (optional) aliases to a {@link Projection.Builder}.
     *
     * @param map Map of projection clauses.
     * @param proj The {@link Projection.Builder} to append to.
     */
    public static void mapToProjection( Map<String, Object> map, Projection.Builder proj ) {
        for ( Entry<String, Object> p : map.entrySet() ) {
            final ProjectionElement.Builder e = proj.addElementsBuilder();
            e.setColumn( ColumnName.newBuilder().setName( p.getKey() ) );
            if ( p.getValue() != null ) {
                e.setAlias( ColumnName.newBuilder().setName( (String) p.getValue() ) );
            }
            proj.addElements( e );
        }
    }


    public static CottontailGrpc.Type getPhysicalTypeRepresentation( AlgDataType algDataType ) {
        PolyType type = algDataType.getPolyType();
        PolyType componentType = algDataType.getComponentType().getPolyType();

        if ( componentType == null ) {
            return getPhysicalTypeRepresentation( type, componentType, 0 );
        } else {
            // TODO js(ct): Verify this call in regards to dimension
            return getPhysicalTypeRepresentation( componentType, type, 0 );
        }
    }


    public static CottontailGrpc.Type getPhysicalTypeRepresentation( PolyType logicalType, PolyType collectionType, int dimension ) {
        if ( collectionType == PolyType.ARRAY ) {
            if ( dimension != 1 ) {
                // Dimension isn't 1, thus we have to serialise the array.
                return Type.STRING;
            }

            switch ( logicalType ) {
                case TINYINT:
                case SMALLINT:
                case INTEGER:
                    return Type.INT_VEC;
                case BIGINT:
                    return Type.LONG_VEC;
                case FLOAT:
                case REAL:
                    return Type.FLOAT_VEC;
                case DOUBLE:
                    return Type.DOUBLE_VEC;
                case BOOLEAN:
                    return Type.BOOL_VEC;
                default:
                    return Type.STRING;
            }
        } else if ( collectionType == null ) {

            switch ( logicalType ) {
                // Natively supported types
                case BOOLEAN:
                    return Type.BOOLEAN;
                case INTEGER:
                    return Type.INTEGER;
                case BIGINT:
                    return Type.LONG;
                case DOUBLE:
                    return Type.DOUBLE;
                case REAL:
                case FLOAT:
                    return Type.FLOAT;
                case VARCHAR:
                case CHAR:
                case JSON:
                    return Type.STRING;
                // Types that require special treatment.
                case TINYINT:
                case SMALLINT:
                case DATE:
                case TIME:
                    return Type.INTEGER;
                case TIMESTAMP:
                    return Type.DATE;
                case DECIMAL:
                case VARBINARY:
                case BINARY:
                    return Type.STRING;
                case FILE:
                case IMAGE:
                case AUDIO:
                case VIDEO:
                    return Type.STRING;
            }
        }

        throw new GenericRuntimeException( "Type " + logicalType + " is not supported by the Cottontail DB adapter." );
    }


    public static Expression rexDynamicParamToDataExpression( RexDynamicParam dynamicParam, ParameterExpression dynamicParameterMap_, PolyType actualType ) {
        return Expressions.call(
                COTTONTAIL_SIMPLE_CONSTANT_TO_DATA_METHOD,
                Expressions.call(
                        dynamicParameterMap_,
                        BuiltInMethod.MAP_GET.method,
                        Expressions.constant( dynamicParam.getIndex() ) ),
                Expressions.constant( actualType ),
                Expressions.constant( dynamicParam.getType() != null ?
                        dynamicParam.getType().getComponentType() != null ?
                                dynamicParam.getType().getComponentType().getPolyType()
                                : null
                        : null ) );
    }


    public static Expression rexLiteralToDataExpression( RexLiteral rexLiteral, PolyType actualType ) {
        ConstantExpression constantExpression;
        if ( rexLiteral.isNull() ) {
            constantExpression = Expressions.constant( null );
        } else {
            switch ( actualType ) {
                case BOOLEAN:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Boolean.class ) );
                    break;
                case INTEGER:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Integer.class ) );
                    break;
                case BIGINT:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Long.class ) );
                    break;
                case DOUBLE:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Double.class ) );
                    break;
                case REAL:
                case FLOAT:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Float.class ) );
                    break;
                case VARCHAR:
                case CHAR:
                case JSON:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( String.class ) );
                    break;
                case DATE:
                case TIME:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Integer.class ) );
                    break;
                case TIMESTAMP:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Long.class ) );
                    break;
                case TINYINT:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Byte.class ) );
                    break;
                case SMALLINT:
                    constantExpression = Expressions.constant( rexLiteral.getValueAs( Short.class ) );
                    break;
                case DECIMAL:
                    BigDecimal bigDecimal = rexLiteral.value.asNumber().BigDecimalValue();
                    constantExpression = Expressions.constant( (bigDecimal != null) ? bigDecimal.toString() : null );
                    break;
                case VARBINARY:
                    constantExpression = Expressions.constant( rexLiteral.value.asBinary().value.toBase64String() );
                case BINARY:
                case FILE:
                case AUDIO:
                case IMAGE:
                case VIDEO:
                    constantExpression = Expressions.constant( rexLiteral.value.asBlob().as64String() );
                    break;
                default:
                    throw new GenericRuntimeException( "Type " + rexLiteral.type + " is not supported by the cottontail adapter." );
            }
        }

        return Expressions.call( COTTONTAIL_SIMPLE_CONSTANT_TO_DATA_METHOD, constantExpression, Expressions.constant( actualType ), Expressions.constant( null ) );
    }


    public static Expression rexArrayConstructorToExpression( RexCall rexCall, PolyType innerType ) {
        Expression constantExpression = arrayListToExpression( rexCall.getOperands(), innerType );
        return Expressions.call( COTTONTAIL_SIMPLE_CONSTANT_TO_DATA_METHOD, constantExpression, Expressions.constant( innerType ), Expressions.constant( null ) );
    }


    public static CottontailGrpc.Literal toData( PolyValue value, PolyType actualType, PolyType parameterComponentType ) {
        final CottontailGrpc.Literal.Builder builder = Literal.newBuilder();
        if ( value == null ) {
            return builder.build();
        }

        log.trace( "Attempting to data value: {}, type: {}", value.getClass().getCanonicalName(), actualType );

        if ( value.isList() ) {
            log.trace( "Attempting to convert an array to data." );
            // TODO js(ct): add list.size() == 0 handling
            // Check whether the decimal array should be converted to a double array (i.e. when we are not comparing to a column of
            // type decimal (which is encoded as string since cottontail does not support the data type decimal))
            if ( parameterComponentType == PolyType.DECIMAL && actualType != PolyType.DECIMAL && actualType != PolyType.ARRAY && value.asList().get( 0 ).isBigDecimal() ) {
                List<PolyValue> numbers = new ArrayList<>( value.asList().size() );
                value.asList().forEach( e -> numbers.add( PolyDouble.of( e.asNumber().doubleValue() ) ) );
                value = PolyList.of( numbers );
            }
            final Vector vector = toVectorData( value );
            if ( vector != null ) {
                return builder.setVectorData( vector ).build();
            } else {
                /* TODO (RG): BigDecimals are currently handled by this branch, which excludes them from being usable for native NNS. */
                return builder.setStringData( org.polypheny.db.adapter.cottontail.util.CottontailSerialisation.GSON.toJson( value ) ).build();
            }
        }

        switch ( actualType ) {
            case BOOLEAN: {
                if ( value.isBoolean() ) {
                    return builder.setBooleanData( value.asBoolean().value ).build();
                }
                break;
            }
            case BIGINT: {
                if ( value.isNumber() ) {
                    return builder.setLongData( value.asNumber().longValue() ).build();
                }
                break;
            }
            case INTEGER:
            case TINYINT:
            case SMALLINT: {
                if ( value.isNumber() ) {
                    return builder.setIntData( value.asNumber().intValue() ).build();
                }
                break;
            }
            case DOUBLE: {
                if ( value.isNumber() ) {
                    return builder.setDoubleData( value.asNumber().doubleValue() ).build();
                }
                break;
            }
            case FLOAT:
            case REAL: {
                if ( value.isNumber() ) {
                    return builder.setFloatData( value.asNumber().floatValue() ).build();
                }
                break;
            }
            case JSON:
            case VARCHAR: {
                if ( value.isString() ) {
                    return builder.setStringData( value.asString().value ).build();
                }
                break;
            }
            case DECIMAL: {
                if ( value.isNumber() ) {
                    return builder.setStringData( value.asNumber().BigDecimalValue().toString() ).build();
                }
                break;
            }
            case TIME: {
                if ( value.isTemporal() ) {
                    return builder.setIntData( value.asTemporal().getMillisOfDay() ).build();
                }
                break;
            }
            case DATE: {
                if ( value.isTemporal() ) {
                    return builder.setIntData( (int) value.asTemporal().getDaysSinceEpoch() ).build();
                }
                break;
            }
            case TIMESTAMP: {
                if ( value.isTemporal() ) {
                    return builder.setDateData( Date.newBuilder().setUtcTimestamp( value.asTemporal().getMilliSinceEpoch() ) ).build();
                }
                break;
            }
            case FILE:
            case IMAGE:
            case AUDIO:
            case VIDEO:
                if ( value.isBlob() ) {
                    return builder.setStringData( value.asBlob().as64String() ).build();
                }
        }

        log.error( "Conversion not possible! value: {}, type: {}", value.getClass().getCanonicalName(), actualType );
        throw new GenericRuntimeException( "Cottontail data type error: Type not handled." );
    }


    /**
     * Converts the provided vectorObject to a {@link Vector} respecting the provided {@link PolyType}
     * of the destination format. Used for NNS.
     *
     * @param vectorObject The vectorObject that should be converted.
     * @param dstElementType The {@link PolyType} of the destination element.
     * @return {@link Vector}
     */
    public static Vector toVectorCallData( Object vectorObject, PolyType dstElementType ) {
        final Vector.Builder builder = Vector.newBuilder();
        switch ( dstElementType ) {
            case TINYINT:
            case SMALLINT:
            case INTEGER:
                for ( Number o : (List<Number>) vectorObject ) {
                    builder.getIntVectorBuilder().addVector( o.intValue() );
                }
                return builder.build();
            case BIGINT:
                for ( Number o : (List<Number>) vectorObject ) {
                    builder.getLongVectorBuilder().addVector( o.longValue() );
                }
                return builder.build();
            case DECIMAL:
            case DOUBLE:
                for ( Number o : (List<Number>) vectorObject ) {
                    builder.getDoubleVectorBuilder().addVector( o.doubleValue() );
                }
                return builder.build();
            case FLOAT:
            case REAL:
                for ( Number o : (List<Number>) vectorObject ) {
                    builder.getFloatVectorBuilder().addVector( o.floatValue() );
                }
                return builder.build();
            default:
                throw new GenericRuntimeException( "Unsupported type: " + dstElementType.getName() );
        }
    }


    /**
     * Converts list of primitive data types (i.e. {@link Double}, {@link Float}, {@link Long}, {@link Integer} or
     * {@link Boolean}) to a {@link Vector} usable by Cottontail DB.
     *
     * @param vectorObject List of {@link Object}s that need to be converted.
     * @return Converted object or null if conversion is not possible.
     */
    public static Vector toVectorData( Object vectorObject ) {
        final Vector.Builder vectorBuilder = Vector.newBuilder();
        // TODO js(ct): add list.size() == 0 handling
        final Object firstItem = ((List) vectorObject).get( 0 );
        if ( firstItem instanceof Byte ) {
            return vectorBuilder.setIntVector(
                    IntVector.newBuilder().addAllVector( ((List<Byte>) vectorObject).stream().map( Byte::intValue ).collect( Collectors.toList() ) ).build() ).build();
        } else if ( firstItem instanceof Short ) {
            return vectorBuilder.setIntVector(
                    IntVector.newBuilder().addAllVector( ((List<Short>) vectorObject).stream().map( Short::intValue ).collect( Collectors.toList() ) ).build() ).build();
        } else if ( firstItem instanceof Integer ) {
            return vectorBuilder.setIntVector(
                    IntVector.newBuilder().addAllVector( (List<Integer>) vectorObject ) ).build();
        } else if ( firstItem instanceof Double ) {
            return vectorBuilder.setDoubleVector(
                    DoubleVector.newBuilder().addAllVector( (List<Double>) vectorObject ) ).build();
        } else if ( firstItem instanceof Long ) {
            return vectorBuilder.setLongVector(
                    LongVector.newBuilder().addAllVector( (List<Long>) vectorObject ) ).build();
        } else if ( firstItem instanceof Float ) {
            return vectorBuilder.setFloatVector(
                    FloatVector.newBuilder().addAllVector( (List<Float>) vectorObject ) ).build();
        } else if ( firstItem instanceof Boolean ) {
            return vectorBuilder.setBoolVector(
                    BoolVector.newBuilder().addAllVector( (List<Boolean>) vectorObject ) ).build();
        } else {
            return null;
        }
    }


    private static Expression arrayListToExpression( List<RexNode> operands, PolyType innerType ) {
        List<Object> list = arrayCallToList( operands, innerType );

        switch ( innerType ) {
            case DECIMAL: {
                List<Object> stringEncoded = convertBigDecimalArray( list );
                return Expressions.call(
                        Types.lookupMethod( Linq4JFixer.class, "fixBigDecimalArray", List.class ),
                        Expressions.constant( stringEncoded ) );
            }
            default:
                return Expressions.constant( list );
        }
    }


    private static List convertBigDecimalArray( List<Object> bigDecimalArray ) {
        List<Object> fixedList = new ArrayList<>( bigDecimalArray.size() );
        for ( Object o : bigDecimalArray ) {
            if ( o instanceof BigDecimal ) {
                fixedList.add( ((BigDecimal) o).toString() );
            } else {
                fixedList.add( convertBigDecimalArray( (List) o ) );
            }
        }
        return fixedList;
    }


    private static List<Object> arrayCallToList( List<RexNode> operands, PolyType innerType ) {
        List<Object> list = new ArrayList<>( operands.size() );
        for ( RexNode node : operands ) {
            if ( node instanceof RexLiteral ) {
                list.add( rexLiteralToJavaClass( (RexLiteral) node, innerType ) );
            } else if ( node instanceof RexCall ) {
                list.add( arrayCallToList( ((RexCall) node).operands, innerType ) );
            } else {
                throw new GenericRuntimeException( "Invalid array." );
            }
        }

        return list;
    }


    private static Object rexLiteralToJavaClass( RexLiteral rexLiteral, PolyType actualType ) {
        switch ( actualType ) {
            case BOOLEAN:
                return rexLiteral.getValueAs( Boolean.class );
            case INTEGER:
                return rexLiteral.getValueAs( Integer.class );
            case BIGINT:
                return rexLiteral.getValueAs( Long.class );
            case DOUBLE:
                return rexLiteral.getValueAs( Double.class );
            case REAL:
            case FLOAT:
                return rexLiteral.getValueAs( Float.class );
            case VARCHAR:
            case CHAR:
            case JSON:
                return rexLiteral.getValueAs( String.class );
            case TIMESTAMP:
                return rexLiteral.getValueAs( Long.class );
            case DATE:
            case TIME:
                return rexLiteral.getValueAs( Integer.class );
            case DECIMAL:
                return rexLiteral.getValueAs( BigDecimal.class );
            case VARBINARY:
            case BINARY:
                return rexLiteral.getValueAs( ByteString.class );
            case TINYINT:
                return rexLiteral.getValueAs( Byte.class );
            case SMALLINT:
                return rexLiteral.getValueAs( Short.class );
            default:
                throw new GenericRuntimeException( "Type " + actualType + " is not supported by the cottontail adapter." );
        }
    }


    public static From fromFromTableAndSchema( String table, String schema ) {
        return From.newBuilder().setScan( Scan.newBuilder().setEntity( EntityName.newBuilder().setName( table ).setSchema( SchemaName.newBuilder().setName( schema ) ) ) ).build();
    }


    /**
     * Converts the given {@link RexCall} to an {@link Expression} for the distance function invocation.
     *
     * @param knnCall {@link RexCall} to convert.
     * @param physicalColumnNames List of physical column names
     * @param alias The alias used to name the resulting field.
     * @return {@link Expression}
     */
    public static Expression knnCallToFunctionExpression( RexCall knnCall, List<String> physicalColumnNames, String alias ) {
        BlockBuilder inner = new BlockBuilder();
        ParameterExpression dynamicParameterMap_ = Expressions.parameter( Modifier.FINAL, Map.class, inner.newName( "dynamicParameters" ) );
        final Expression probingArgument = knnCallTargetColumn( knnCall.getOperands().get( 0 ), physicalColumnNames, dynamicParameterMap_ );
        final Expression queryArgument = knnCallVector( knnCall.getOperands().get( 1 ), dynamicParameterMap_, knnCall.getOperands().get( 0 ).getType().getComponentType().getPolyType() );
        final Expression distance = knnCallDistance( knnCall.getOperands().get( 2 ), dynamicParameterMap_ );
        return Expressions.lambda( Expressions.block( Expressions.return_( null, Expressions.call( COTTONTAIL_KNN_BUILDER_METHOD, probingArgument, queryArgument, distance, Expressions.constant( alias ) ) ) ), dynamicParameterMap_ );
    }


    /**
     * Converts the given {@link RexNode} to an {@link Expression} for the target column for a distance function invocation.
     *
     * @param node {@link RexNode} to convert
     * @param physicalColumnNames List of physical column names
     * @return {@link Expression}
     */
    private static Expression knnCallTargetColumn( RexNode node, List<String> physicalColumnNames, ParameterExpression dynamicParamMap ) {
        if ( node instanceof RexIndexRef ) {
            RexIndexRef inputRef = (RexIndexRef) node;
            return Expressions.constant( physicalColumnNames.get( inputRef.getIndex() ) );
        } else if ( node instanceof RexDynamicParam ) {
            RexDynamicParam dynamicParam = (RexDynamicParam) node;
            return Expressions.call( dynamicParamMap, BuiltInMethod.MAP_GET.method, Expressions.constant( dynamicParam.getIndex() ) );
        }

        throw new GenericRuntimeException( "First argument is neither an input ref nor a dynamic parameter" );
    }


    /**
     * Converts the given {@link RexNode} to an {@link Expression} for the query vector for a distance function invocation.
     *
     * @param node {@link RexNode} to convert
     * @param actualType The {@link PolyType} of the array elements. Required for proper conversion!
     * @return {@link Expression}
     */
    private static Expression knnCallVector( RexNode node, ParameterExpression dynamicParamMap, PolyType actualType ) {
        if ( (node instanceof RexCall) && (((RexCall) node).getOperator() instanceof SqlArrayValueConstructor) ) {
            final Expression arrayList = arrayListToExpression( ((RexCall) node).getOperands(), actualType );
            return Expressions.call( CottontailTypeUtil.class, "toVectorCallData", arrayList, Expressions.constant( actualType ) );
        } else if ( node instanceof RexDynamicParam ) {
            final RexDynamicParam dynamicParam = (RexDynamicParam) node;
            final MethodCallExpression listExpression = Expressions.call( dynamicParamMap, BuiltInMethod.MAP_GET.method, Expressions.constant( dynamicParam.getIndex() ) );
            return Expressions.call( CottontailTypeUtil.class, "toVectorCallData", listExpression, Expressions.constant( actualType ) );
        }

        throw new GenericRuntimeException( "Argument is neither an array call nor a dynamic parameter" );
    }


    /**
     * Converts the given {@link RexNode} to an {@link Expression} for the name of the distance function.
     *
     * @param node {@link RexNode} to convert
     * @return {@link Expression}
     */
    private static Expression knnCallDistance( RexNode node, ParameterExpression dynamicParamMap ) {
        if ( node instanceof RexLiteral ) {
            return Expressions.constant( ((RexLiteral) node).getValue2() );
        } else if ( node instanceof RexDynamicParam ) {
            RexDynamicParam dynamicParam = (RexDynamicParam) node;
            return Expressions.call( dynamicParamMap, BuiltInMethod.MAP_GET.method,
                    Expressions.constant( dynamicParam.getIndex() ) );
        }

        throw new GenericRuntimeException( "Argument is neither an array call nor a dynamic parameter" );
    }


    /*
    public static SqlLiteral defaultValueParser( LogicalDefaultValue logicalDefaultValue, PolyType actualType ) {
        if ( actualType == PolyType.ARRAY ) {
            throw new GenericRuntimeException( "Default values are not supported for array types" );
        }

        SqlLiteral literal;
        switch ( actualType ) {
            case BOOLEAN:
                literal = Boolean.parseBoolean( logicalDefaultValue.value.toJson() );
                break;
            case INTEGER:
                literal = SqlLiteral.createExactNumeric( logicalDefaultValue.value.toJson(), ParserPos.ZERO ).getValueAs( Integer.class );
                break;
            case DECIMAL:
                literal = SqlLiteral.createExactNumeric( logicalDefaultValue.value.toJson(), ParserPos.ZERO ).getValueAs( BigDecimal.class );
                break;
            case BIGINT:
                literal = SqlLiteral.createExactNumeric( logicalDefaultValue.value.toJson(), ParserPos.ZERO ).getValueAs( Long.class );
                break;
            case REAL:
            case FLOAT:
                literal = SqlLiteral.createApproxNumeric( logicalDefaultValue.value.toJson(), ParserPos.ZERO ).getValueAs( Float.class );
                break;
            case DOUBLE:
                literal = SqlLiteral.createApproxNumeric( logicalDefaultValue.value.toJson(), ParserPos.ZERO ).getValueAs( Double.class );
                break;
            case VARCHAR:
                literal = logicalDefaultValue.value;
                break;
            default:
                throw new PolyphenyDbException( "Not yet supported default value type: " + actualType );
        }

        return literal;
    }*/

}