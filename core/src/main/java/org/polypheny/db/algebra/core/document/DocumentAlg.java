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

package org.polypheny.db.algebra.core.document;

import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.plan.AlgOptEntity;


/**
 * {@link org.polypheny.db.schema.ModelTrait#DOCUMENT} native node.
 */
public interface DocumentAlg {

    DocType getDocType();

    default AlgOptEntity getCollection() {
        assert this instanceof AlgNode;
        return ((AlgNode) this).getEntity();
    }

    enum DocType {
        SCAN, FILTER, VALUES, PROJECT, AGGREGATE, SORT, MODIFY
    }

}
