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

package org.polypheny.db.webui.models.results;


import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.webui.HttpServer;
import org.polypheny.db.webui.models.catalog.FieldDefinition;
import org.polypheny.db.webui.models.catalog.UiColumnDefinition;
import org.polypheny.db.webui.models.requests.UIRequest;


/**
 * Contains data from a query, the titles of the columns and information about the pagination
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Value
@NonFinal
public class RelationalResult extends Result<String[], UiColumnDefinition> {

    /**
     * Information for the pagination: what current page is being displayed
     */
    public int currentPage;
    /**
     * Information for the pagination: how many pages there can be in total
     */
    public int highestPage;


    /**
     * Table from which the data has been fetched
     */
    public String table;
    /**
     * List of tables of a schema
     */
    public String[] tables;
    /**
     * The request from the UI is being sent back and contains information about which columns are being filtered and which are being sorted
     */
    public UIRequest request;

    /**
     * Number of affected rows
     */
    public int affectedRows;

    /**
     * ExpressionType of the result: if the data is from a table/view/arbitrary query
     */
    public ResultType type;

    /**
     * language type of result MQL/SQL/CQL
     */
    public QueryLanguage language = QueryLanguage.from( "sql" );

    /**
     * Indicate that only a subset of the specified query is being displayed.
     */
    public boolean hasMoreRows;


    /**
     * Deserializer Constructor, which is able to create a Result from its
     * serialized form
     *
     * @param in the reader, which contains the Result
     */
    public static RelationalResult create( JsonReader in ) throws IOException {
        RelationalResultBuilder<?, ?> builder = RelationalResult.builder();
        while ( in.peek() != JsonToken.END_OBJECT ) {
            switch ( in.nextName() ) {
                case "header":
                    in.beginArray();
                    TypeAdapter<UiColumnDefinition> serializer = UiColumnDefinition.serializer;
                    List<UiColumnDefinition> cols = new ArrayList<>();
                    while ( in.peek() != JsonToken.END_ARRAY ) {
                        cols.add( serializer.read( in ) );
                    }
                    in.endArray();
                    builder.header( cols.toArray( new UiColumnDefinition[0] ) );
                    break;
                case "data":
                    builder.data( extractNestedArray( in ) );
                    break;
                case "currentPage":
                    builder.currentPage( in.nextInt() );
                    break;
                case "highestPage":
                    builder.highestPage( in.nextInt() );
                    break;
                case "table":
                    builder.table( in.nextString() );
                    break;
                case "tables":
                    builder.tables( extractArray( in ).toArray( new String[0] ) );
                    break;
                case "request":
                    builder.request( UIRequest.getSerializer().read( in ) );
                    break;
                case "error":
                    builder.error( in.nextString() );
                    break;
                case "exception":
                    builder.exception( HttpServer.throwableTypeAdapter.read( in ) );
                    break;
                case "affectedRows":
                    builder.affectedRows( in.nextInt() );
                    break;
                case "generatedQuery":
                    builder.generatedQuery( in.nextString() );
                    break;
                case "type":
                    builder.type( extractEnum( in, ResultType::valueOf ) );
                    break;
                case "namespaceType":
                    builder.namespaceType( extractEnum( in, NamespaceType::valueOf ) );
                    break;
                case "namespaceName":
                    builder.namespaceName( in.nextString() );
                    break;
                case "language":
                    builder.language( QueryLanguage.getSerializer().read( in ) );
                    break;
                case "hasMoreRows":
                    builder.hasMoreRows( in.nextBoolean() );
                    break;
                case "xid":
                    builder.xid( in.nextString() );
                    break;
                default:
                    throw new RuntimeException( "There was an unrecognized column while deserializing Result." );
            }
        }
        return builder.build();

    }


    private static String[][] extractNestedArray( JsonReader in ) throws IOException {
        if ( in.peek() == JsonToken.NULL ) {
            in.nextNull();
            return null;
        }
        in.beginArray();
        List<List<String>> rawData = new ArrayList<>();
        while ( in.peek() != JsonToken.END_ARRAY ) {
            List<String> list = extractArray( in );
            rawData.add( list );
        }
        in.endArray();
        return toNestedArray( rawData );
    }


    private static String[][] toNestedArray( List<List<String>> nestedList ) {
        String[][] array = new String[nestedList.size()][];
        int i = 0;
        for ( List<String> list : nestedList ) {
            array[i] = list.toArray( new String[0] );
            i++;
        }

        return array;
    }


    @NotNull
    private static List<String> extractArray( JsonReader in ) throws IOException {
        if ( in.peek() == JsonToken.NULL ) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        in.beginArray();
        while ( in.peek() != JsonToken.END_ARRAY ) {
            if ( in.peek() == JsonToken.NULL ) {
                in.nextNull();
                list.add( null );
            } else if ( in.peek() == JsonToken.STRING ) {
                list.add( in.nextString() );
            } else {
                throw new RuntimeException( "Error while un-parsing Result." );
            }
        }
        in.endArray();
        return list;
    }


    private static <T extends Enum<?>> T extractEnum( JsonReader in, Function<String, T> enumFunction ) throws IOException {
        if ( in.peek() == JsonToken.NULL ) {
            in.nextNull();
            return null;
        } else {
            return enumFunction.apply( in.nextString() );
        }
    }


    public static RelationalResultBuilder<?, ?> builder() {
        return new RelationalResultBuilderImpl();
    }


    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson( this );
    }


    public static TypeAdapter<RelationalResult> getSerializer() {
        return new TypeAdapter<>() {

            @Override
            public void write( JsonWriter out, RelationalResult result ) throws IOException {
                if ( result == null ) {
                    out.nullValue();
                    return;
                }

                out.beginObject();
                out.name( "header" );
                handleDbColumns( out, result );
                out.name( "data" );
                handleNestedArray( out, result.data );
                out.name( "currentPage" );
                out.value( result.currentPage );
                out.name( "highestPage" );
                out.value( result.highestPage );
                out.name( "table" );
                out.value( result.table );
                out.name( "tables" );
                handleArray( out, result.tables );
                out.name( "request" );
                UIRequest.getSerializer().write( out, result.request );
                out.name( "error" );
                out.value( result.error );
                out.name( "exception" );
                HttpServer.throwableTypeAdapter.write( out, result.exception );
                out.name( "affectedRows" );
                out.value( result.affectedRows );
                out.name( "generatedQuery" );
                out.value( result.query );
                out.name( "type" );
                handleEnum( out, result.type );
                out.name( "namespaceType" );
                handleEnum( out, result.namespaceType );
                out.name( "namespaceName" );
                out.value( result.namespaceName );
                out.name( "language" );
                QueryLanguage.getSerializer().write( out, result.language );
                out.name( "hasMoreRows" );
                out.value( result.hasMoreRows );
                out.name( "xid" );
                out.value( result.xid );
                out.endObject();
            }


            private void handleDbColumns( JsonWriter out, RelationalResult result ) throws IOException {
                if ( result.header == null ) {
                    out.nullValue();
                    return;
                }
                out.beginArray();

                for ( FieldDefinition column : result.header ) {
                    if ( column instanceof UiColumnDefinition ) {
                        UiColumnDefinition.serializer.write( out, (UiColumnDefinition) column );
                    } else {
                        FieldDefinition.serializer.write( out, column );
                    }
                }
                out.endArray();
            }


            private void handleArray( JsonWriter out, String[] result ) throws IOException {
                if ( result == null ) {
                    out.nullValue();
                    return;
                }
                out.beginArray();
                for ( String table : result ) {
                    out.value( table );
                }
                out.endArray();
            }


            private void handleNestedArray( JsonWriter out, String[][] result ) throws IOException {
                if ( result == null ) {
                    out.nullValue();
                    return;
                }
                out.beginArray();
                for ( String[] data : result ) {
                    handleArray( out, data );
                }
                out.endArray();
            }


            private void handleEnum( JsonWriter out, Enum<?> enums ) throws IOException {
                if ( enums == null ) {
                    out.nullValue();
                } else {
                    out.value( enums.name() );
                }
            }


            @Override
            public RelationalResult read( JsonReader in ) throws IOException {
                if ( in.peek() == null ) {
                    in.nextNull();
                    return null;
                }
                in.beginObject();
                RelationalResult res = RelationalResult.create( in );
                in.endObject();
                return res;
            }

        };
    }


    /**
     * Remove when bugs in SuperBuilder regarding generics are fixed
     */

    public static abstract class RelationalResultBuilder<C extends RelationalResult, B extends RelationalResultBuilder<C, B>> extends ResultBuilder<String[], UiColumnDefinition, C, B> {

        private int currentPage;
        private int highestPage;
        private String table;
        private String[] tables;
        private UIRequest request;
        private Throwable exception;
        private int affectedRows;
        private String generatedQuery;
        private ResultType type;
        private QueryLanguage language$value;
        private boolean language$set;
        private boolean hasMoreRows;


        public B currentPage( int currentPage ) {
            this.currentPage = currentPage;
            return self();
        }


        public B highestPage( int highestPage ) {
            this.highestPage = highestPage;
            return self();
        }


        public B table( String table ) {
            this.table = table;
            return self();
        }


        public B tables( String[] tables ) {
            this.tables = tables;
            return self();
        }


        public B request( UIRequest request ) {
            this.request = request;
            return self();
        }


        public B exception( Throwable exception ) {
            this.exception = exception;
            return self();
        }


        public B affectedRows( int affectedRows ) {
            this.affectedRows = affectedRows;
            return self();
        }


        public B generatedQuery( String generatedQuery ) {
            this.generatedQuery = generatedQuery;
            return self();
        }


        public B type( ResultType type ) {
            this.type = type;
            return self();
        }


        public B language( QueryLanguage language ) {
            this.language$value = language;
            this.language$set = true;
            return self();
        }


        public B hasMoreRows( boolean hasMoreRows ) {
            this.hasMoreRows = hasMoreRows;
            return self();
        }


    }


}
