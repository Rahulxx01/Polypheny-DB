/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.schema.graph;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Getter;
import org.polypheny.db.algebra.type.AlgDataTypeSystem;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.runtime.PolyCollections.PolyDirectory;
import org.polypheny.db.runtime.PolyCollections.PolyList;
import org.polypheny.db.type.BasicPolyType;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Collation;
import org.polypheny.db.util.NlsString;

@Getter
public abstract class GraphPropertyHolder extends GraphObject {


    public final PolyDirectory properties;
    public final PolyList<String> labels;


    public GraphPropertyHolder( String id, GraphObjectType type, PolyDirectory properties, List<String> labels ) {
        super( id, type );
        this.properties = properties;
        this.labels = new PolyList<>( labels );
    }


    public PolyList<RexLiteral> getRexLabels() {
        return labels
                .stream()
                .map( l -> new RexLiteral( new NlsString( l, StandardCharsets.ISO_8859_1.name(), Collation.IMPLICIT ), new BasicPolyType( AlgDataTypeSystem.DEFAULT, PolyType.VARCHAR, 255 ), PolyType.CHAR ) )
                .collect( Collectors.toCollection( PolyList::new ) );
    }


    public boolean matchesProperties( PolyDirectory properties ) {
        if ( this.properties.size() != properties.size() ) {
            return false;
        }

        for ( Entry<String, Object> entry : this.properties.entrySet() ) {
            if ( !properties.containsKey( entry.getKey() ) ) {
                return false;
            }
            if ( !properties.get( entry.getKey() ).equals( entry.getValue() ) ) {
                return false;
            }
        }
        return true;

    }


    public boolean matchesLabels( List<String> labels ) {
        return this.labels.equals( labels );
    }


    public boolean labelAndPropertyMatch( GraphPropertyHolder other ) {
        // we dont need to match the source and target, this is done via segements or paths
        if ( !other.properties.isEmpty() ) {
            if ( !matchesProperties( other.properties ) ) {
                return false;
            }
        }
        if ( !other.labels.isEmpty() ) {
            if ( !matchesLabels( other.labels ) ) {
                return false;
            }
        }

        return true;
    }

}
