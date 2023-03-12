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

package org.polypheny.db.catalog.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.schema.Wrapper;

@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class CatalogEntityPlacement implements CatalogObject, Serializable, Wrapper {

    public final Long namespaceId;
    public final Long adapterId;
    public final Long entityId;


}
