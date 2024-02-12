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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.adapter.mongodb;


import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSBucket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.Document;
import org.polypheny.db.adapter.mongodb.util.MongoTupleType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyBinary;
import org.polypheny.db.type.entity.PolyBoolean;
import org.polypheny.db.type.entity.PolyList;
import org.polypheny.db.type.entity.PolyLong;
import org.polypheny.db.type.entity.PolyNull;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.document.PolyDocument;
import org.polypheny.db.type.entity.numerical.PolyBigDecimal;
import org.polypheny.db.type.entity.numerical.PolyDouble;
import org.polypheny.db.type.entity.numerical.PolyInteger;
import org.polypheny.db.type.entity.temporal.PolyDate;
import org.polypheny.db.type.entity.temporal.PolyTime;
import org.polypheny.db.type.entity.temporal.PolyTimestamp;
import org.polypheny.db.util.BsonUtil;


/**
 * Enumerator that reads from a MongoDB collection.
 */
class MongoEnumerator implements Enumerator<PolyValue[]> {

    protected final Iterator<PolyValue[]> cursor;
    protected final GridFSBucket bucket;
    protected PolyValue[] current;


    /**
     * Creates a MongoEnumerator.
     *
     * @param cursor Mongo iterator (usually a {@link com.mongodb.ServerCursor})
     */
    MongoEnumerator( Iterator<PolyValue[]> cursor, GridFSBucket bucket ) {
        this.cursor = cursor;
        this.bucket = bucket;
    }


    @Override
    public PolyValue[] current() {
        return current;
    }


    @Override
    public boolean moveNext() {
        try {
            if ( cursor.hasNext() ) {
                current = cursor.next();

                //current = handleTransforms( current );

                return true;
            } else {
                current = null;
                return false;
            }
        } catch ( Exception e ) {
            throw new GenericRuntimeException( e );
        }
    }


    /*protected PolyValue handleTransforms( Bson current ) {
        if ( current == null ) {
            return null;
        }
        if ( current.getClass().isArray() ) {
            List<PolyValue> temp = new ArrayList<>();
            for ( Bson el : (Bson[]) current ) {
                temp.add( handleTransforms( el ) );
            }
            return temp.toArray();
        } else {
            if ( current instanceof List ) {
                return PolyList.of((List<Bson>) current).stream().map( this::handleTransforms ).toList();
            } else if ( current instanceof Document ) {
                return handleDocument( (Document) current );
            }
        }
        return current;
    }*/

    // s -> stream
    /*private PolyValue handleDocument( Document el ) {
        if ( el.containsKey( "_type" ) ) {
            String type = el.getString( "_type" );
            if ( type.equals( "s" ) ) {
                // if we have inserted a document and have distributed chunks which we have to fetch
                ObjectId objectId = new ObjectId( (String) ((Document) current).get( "_id" ) );
                GridFSDownloadStream stream = bucket.openDownloadStream( objectId );
                return new PushbackInputStream( stream );
            }
            throw new GenericRuntimeException( "The document type was not recognized" );
        } else {
            return el.toJson();
        }
    }*/


    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void close() {
        if ( cursor instanceof MongoCursor ) {
            ((MongoCursor<?>) cursor).close();
        }
        // AggregationOutput implements Iterator but not DBCursor. There is no available close() method -- apparently there is no open resource.
    }


    static <E> Function1<Document, E> mapGetter() {
        return a0 -> (E) a0;
    }


    /**
     * This method is needed to translate the special types back to their initial ones in Arrays,
     * for example Float is not available in MongoDB and has to be stored as Double,
     * This needs to be fixed when retrieving the arrays.
     * Additionally, for array we cannot be sure how the value is stored, as we lose this information on insert
     */
    static List<PolyValue> arrayGetter( List<Object> objects, Class<? extends PolyValue> arrayFieldClass ) {
        /*if ( arrayFieldClass == Float.class || arrayFieldClass == float.class ) {
            if ( objects.size() > 1 ) {
                if ( objects.get( 0 ) instanceof Double ) {
                    return objects.stream().map( o -> ((Double) o).floatValue() ).collect( Collectors.toList() );
                } else if ( objects.get( 0 ) instanceof Decimal128 ) {
                    return objects.stream().map( obj -> ((Decimal128) obj).floatValue() ).collect( Collectors.toList() );
                }
            }
            return objects;
        } else if ( arrayFieldClass == BigDecimal.class ) {
            return objects.stream().map( obj -> ((Decimal128) obj).bigDecimalValue() ).collect( Collectors.toList() );
        } else if ( arrayFieldClass == double.class ) {
            if ( objects.size() > 1 ) {
                if ( objects.get( 0 ) instanceof Decimal128 ) {
                    return objects.stream().map( o -> ((Decimal128) o).doubleValue() ).collect( Collectors.toList() );
                }
            }
            return objects;
        } else if ( arrayFieldClass == long.class ) {
            if ( objects.size() > 1 ) {
                if ( objects.get( 0 ) instanceof Integer ) {
                    return objects.stream().map( o -> Long.valueOf( (Integer) o ) ).collect( Collectors.toList() );
                }
            }
            return objects;
        } else {
            return objects;
        }*/
        return null;
    }


    static Function1<Document, PolyValue> singletonGetter( final MongoTupleType type ) {
        return a0 -> convert( a0.toBsonDocument().get( type.name ), type );
    }


    /**
     *
     */
    static Function1<Document, PolyValue[]> listGetter( final MongoTupleType type ) {
        /*return a0 -> {
            PolyValue[] objects = new PolyValue[fields.size()];
            for ( int i = 0; i < fields.size(); i++ ) {
                final Map.Entry<String, Class<? extends PolyValue>> field = fields.get( i );
                final String name = field.getKey();

                objects[i] = convert( a0.get( name ), field.getValue() );

                if ( field.getValue() == List.class ) {
                    objects[i] = arrayGetter( (List) objects[i], arrayFields.get( i ).getValue() );
                }
            }
            return objects;
        };*/
        List<Function<BsonValue, PolyValue>> trans = new ArrayList<>();
        for ( MongoTupleType sub : type.subs ) {
            trans.add( o -> convert( o.asDocument().get( sub.name ), sub ) );
        }
        if ( type.type == PolyType.DOCUMENT ) {
            trans.add( o -> convert( o, type ) );
        }

        return e -> {
            BsonDocument doc = e.toBsonDocument();

            return trans.stream().map( t -> t.apply( doc ) ).toArray( PolyValue[]::new );
        };
    }


    static Function1<Document, PolyValue[]> getter( MongoTupleType tupleType ) {
        return tupleType == null
                ? mapGetter()
                : listGetter( tupleType );
    }


    private static PolyValue convert( BsonValue o, MongoTupleType type ) {
        if ( o == null || o.isNull() ) {
            return new PolyNull();
        }

        return switch ( type.type ) {
            case BIGINT -> PolyLong.of( o.asNumber().longValue() );
            case INTEGER, SMALLINT, TINYINT -> PolyInteger.of( o.asNumber().longValue() );
            case BOOLEAN -> PolyBoolean.of( o.asBoolean().getValue() );
            case TEXT, CHAR, VARCHAR -> PolyString.of( o.asString().getValue() );
            case DECIMAL -> {
                if ( o.isNumber() ) {
                    yield PolyBigDecimal.of( o.asNumber().doubleValue() );
                } else if ( o.isDecimal128() ) {
                    yield PolyBigDecimal.of( o.asDecimal128().decimal128Value().bigDecimalValue() );
                } else {
                    throw new NotImplementedException();
                }
            }
            case FLOAT, REAL, DOUBLE -> {
                if ( o.isNumber() ) {
                    yield PolyDouble.of( o.asNumber().doubleValue() );
                } else if ( o.isDecimal128() ) {
                    yield PolyDouble.of( o.asDecimal128().decimal128Value().bigDecimalValue().doubleValue() );
                } else {
                    throw new NotImplementedException();
                }
            }
            case BINARY -> PolyBinary.of( o.asBinary().getData() );
            case TIMESTAMP -> PolyTimestamp.of( o.asNumber().longValue() );
            case TIME -> PolyTime.of( o.asNumber().longValue() );
            case DATE -> PolyDate.of( o.asNumber().longValue() );
            case DOCUMENT -> polyDocumentFromBson( o.asDocument() );
            case ARRAY -> BsonUtil.toPolyValue( o.asArray() );
            default -> throw new NotImplementedException();
        };

    }


    private static PolyDocument polyDocumentFromBson( BsonDocument document ) {
        PolyDocument doc = new PolyDocument();
        for ( String key : document.keySet() ) {
            doc.put( PolyString.of( key ), convert( document.get( key ), document.get( key ).getBsonType() ) );
        }
        return doc;
    }


    private static PolyValue convert( BsonValue value, BsonType bsonType ) {
        return switch ( bsonType ) {
            case DOUBLE -> PolyDouble.of( value.asDouble().getValue() );
            case STRING -> PolyString.of( value.asString().getValue() );
            case DOCUMENT -> polyDocumentFromBson( value.asDocument() );
            case ARRAY -> PolyList.of( value.asArray().getValues().stream().map( v -> convert( v, v.getBsonType() ) ).toArray( PolyValue[]::new ) );
            case BINARY -> PolyBinary.of( value.asBinary().getData() );
            case OBJECT_ID -> PolyString.of( value.asObjectId().getValue().toHexString() );
            case BOOLEAN -> PolyBoolean.of( value.asBoolean().getValue() );
            case DATE_TIME -> PolyTimestamp.of( value.asDateTime().getValue() );
            case NULL -> PolyNull.NULL;
            case INT32 -> PolyInteger.of( value.asInt32().getValue() );
            case TIMESTAMP -> PolyTimestamp.of( value.asTimestamp().getValue() );
            case INT64 -> PolyLong.of( value.asInt64().getValue() );
            case DECIMAL128 -> PolyBigDecimal.of( value.asDecimal128().decimal128Value().bigDecimalValue() );
            default -> throw new NotImplementedException();
        };
    }


}

