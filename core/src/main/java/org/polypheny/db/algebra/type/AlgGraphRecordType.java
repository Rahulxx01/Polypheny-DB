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

package org.polypheny.db.algebra.type;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.polypheny.db.type.BasicPolyType;
import org.polypheny.db.type.PolyType;

public class AlgGraphRecordType extends AlgRecordType {

    public AlgGraphRecordType( List<AlgDataTypeField> fields ) {
        super( fields );
    }


    public AlgGraphRecordType() {
        super( ImmutableList.of( new AlgDataTypeFieldImpl( "_data", 0, new BasicPolyType( AlgDataTypeSystem.DEFAULT, PolyType.GRAPH ) ) ) );
    }


    @Override
    public PolyType getPolyType() {
        return PolyType.GRAPH;
    }

}
