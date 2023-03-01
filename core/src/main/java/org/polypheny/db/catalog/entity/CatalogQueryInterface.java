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

package org.polypheny.db.catalog.entity;


import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.With;


@EqualsAndHashCode
@Value
@With
public class CatalogQueryInterface implements CatalogObject {

    private static final long serialVersionUID = 7212289724539530050L;

    public long id;
    public String name;
    public String clazz;
    public ImmutableMap<String, String> settings;


    public CatalogQueryInterface( final long id, @NonNull final String uniqueName, @NonNull final String clazz, @NonNull final Map<String, String> settings ) {
        this.id = id;
        this.name = uniqueName;
        this.clazz = clazz;
        this.settings = ImmutableMap.copyOf( settings );
    }


    // Used for creating ResultSets
    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[]{ name };
    }


}
