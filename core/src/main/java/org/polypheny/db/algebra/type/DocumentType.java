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

package org.polypheny.db.algebra.type;

import com.google.common.collect.Streams;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import org.polypheny.db.nodes.IntervalQualifier;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Collation;
import org.polypheny.db.util.Pair;

@Data
public class DocumentType implements AlgDataType, AlgDataTypeFamily {

    public static final String DOCUMENT_ID = "_id";
    public static final String DOCUMENT_DATA = "_data";
    public static final Integer ID_SIZE = 2024;
    public static final Integer DATA_SIZE = 2024;
    public StructKind structKind;

    public final List<AlgDataTypeField> fixedFields;


    public String physicalName = null;

    public String digest;


    public DocumentType( @Nonnull List<AlgDataTypeField> fixedFields ) {
        this.structKind = fixedFields.isEmpty() ? StructKind.NONE : StructKind.SEMI;
        this.fixedFields = new ArrayList<>( fixedFields );
        this.digest = computeDigest();
    }


    public DocumentType() {
        this( List.of() );
    }


    public DocumentType( List<? extends RexNode> ids, List<String> names ) {
        this( Streams.mapWithIndex( Pair.zip( ids, names ).stream(), ( p, i ) -> new AlgDataTypeFieldImpl( -1L, p.getRight(), (int) i, p.getLeft().getType() ) ).collect( Collectors.toList() ) );
    }


    public static DocumentType ofId() {
        return new DocumentType( List.of( new AlgDataTypeFieldImpl( -1L, DOCUMENT_ID, 0, new DocumentType( List.of() ) ) ) );
    }


    public static AlgDataType asRelational() {
        return new AlgRecordType( List.of(
                new AlgDataTypeFieldImpl( -1L, DOCUMENT_ID, 0, AlgDataTypeFactory.DEFAULT.createPolyType( PolyType.VARBINARY, 2024 ) ),
                new AlgDataTypeFieldImpl( -1L, DOCUMENT_DATA, 1, AlgDataTypeFactory.DEFAULT.createPolyType( PolyType.VARBINARY, 2024 ) )
        ) );
    }


    public static AlgDataType ofDoc() {
        return new DocumentType( List.of( new AlgDataTypeFieldImpl( -1L, "d", 0, DocumentType.ofId() ) ) );
    }


    private String computeDigest() {
        assert fixedFields != null;
        return getClass().getSimpleName() +
                fixedFields.stream().map( f -> f.getType().getFullTypeString() ).collect( Collectors.joining( "$" ) );
    }


    @Override
    public boolean isStruct() {
        return true;
    }


    @Override
    public List<AlgDataTypeField> getFieldList() {
        return fixedFields;
    }


    @Override
    public List<String> getFieldNames() {
        return getFieldList().stream().map( AlgDataTypeField::getName ).collect( Collectors.toList() );
    }


    @Override
    public List<Long> getFieldIds() {
        return fixedFields.stream().map( AlgDataTypeField::getId ).collect( Collectors.toList() );
    }


    @Override
    public int getFieldCount() {
        return getFieldList().size();
    }


    @Override
    public AlgDataTypeField getField( String fieldName, boolean caseSensitive, boolean elideRecord ) {
        // everything we ask a document for is there
        int index = getFieldNames().indexOf( fieldName );
        if ( index >= 0 ) {
            return getFieldList().get( index );
        }
        AlgDataTypeFieldImpl added = new AlgDataTypeFieldImpl( -1L, fieldName, getFieldCount(), new DocumentType() );
        fixedFields.add( added );
        computeDigest();

        return added;
    }


    @Override
    public boolean isNullable() {
        return false;
    }


    @Override
    public AlgDataType getComponentType() {
        return null;
    }


    @Override
    public Charset getCharset() {
        return null;
    }


    @Override
    public Collation getCollation() {
        return null;
    }


    @Override
    public IntervalQualifier getIntervalQualifier() {
        return null;
    }


    @Override
    public int getPrecision() {
        return 0;
    }


    @Override
    public int getRawPrecision() {
        return 0;
    }


    @Override
    public int getScale() {
        return 0;
    }


    @Override
    public PolyType getPolyType() {
        return PolyType.DOCUMENT;
    }


    @Override
    public String getFullTypeString() {
        return digest;
    }


    @Override
    public AlgDataTypeFamily getFamily() {
        return null;
    }


    @Override
    public AlgDataTypePrecedenceList getPrecedenceList() {
        return null;
    }


    @Override
    public AlgDataTypeComparability getComparability() {
        return AlgDataTypeComparability.ALL;
    }


    @Override
    public boolean isDynamicStruct() {
        return false;
    }


}
