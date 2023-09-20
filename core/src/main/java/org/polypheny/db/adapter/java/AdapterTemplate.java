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

package org.polypheny.db.adapter.java;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Value;
import org.polypheny.db.adapter.AbstractAdapterSetting;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.adapter.AdapterManager.Function4;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.annotations.AdapterProperties;
import org.polypheny.db.catalog.entity.CatalogAdapter.AdapterType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;

@Value
public class AdapterTemplate {

    @Getter
    public Map<String, String> defaultSettings;

    public Class<?> clazz;
    public String adapterName;
    public AdapterType adapterType;
    Function4<Long, String, Map<String, String>, Adapter<?>> deployer;


    public AdapterTemplate( Class<?> clazz, String adapterName, Map<String, String> defaultSettings, Function4<Long, String, Map<String, String>, Adapter<?>> deployer ) {
        this.adapterName = adapterName;
        this.clazz = clazz;
        this.defaultSettings = defaultSettings;
        this.adapterType = getAdapterType( clazz );
        this.deployer = deployer;
    }


    public static AdapterType getAdapterType( Class<?> clazz ) {
        return DataStore.class.isAssignableFrom( clazz ) ? AdapterType.STORE : AdapterType.SOURCE;
    }


    public static AdapterTemplate fromString( String adapterName, AdapterType adapterType ) {
        return AdapterManager.getAdapterType( adapterName, adapterType );
    }


    public List<AbstractAdapterSetting> getAllSettings() {
        AdapterProperties properties = clazz.getAnnotation( AdapterProperties.class );
        if ( clazz.getAnnotation( AdapterProperties.class ) == null ) {
            throw new GenericRuntimeException( "The used adapter does not annotate its properties correctly." );
        }
        return AbstractAdapterSetting.fromAnnotations( clazz.getAnnotations(), properties );
    }

}
