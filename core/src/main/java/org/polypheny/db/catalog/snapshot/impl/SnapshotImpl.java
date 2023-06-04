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

package org.polypheny.db.catalog.snapshot.impl;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.adapter.java.AdapterTemplate;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.LogicalAdapter;
import org.polypheny.db.catalog.entity.LogicalAdapter.AdapterType;
import org.polypheny.db.catalog.entity.LogicalQueryInterface;
import org.polypheny.db.catalog.entity.LogicalUser;
import org.polypheny.db.catalog.entity.logical.LogicalCollection;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.catalog.entity.logical.LogicalGraph;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.logistic.Pattern;
import org.polypheny.db.catalog.snapshot.AllocSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalDocSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalGraphSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalRelSnapshot;
import org.polypheny.db.catalog.snapshot.Snapshot;
import org.polypheny.db.iface.QueryInterfaceManager.QueryInterfaceTemplate;

@Value
@Accessors(fluent = true)
public class SnapshotImpl implements Snapshot {


    @NotNull
    LogicalRelSnapshot rel;
    @NotNull
    LogicalDocSnapshot doc;
    @NotNull
    LogicalGraphSnapshot graph;
    @NotNull
    AllocSnapshot alloc;

    @Getter
    long id;

    ImmutableMap<Long, LogicalUser> users;

    ImmutableMap<String, LogicalUser> userNames;
    ImmutableMap<Long, LogicalQueryInterface> interfaces;
    ImmutableMap<String, QueryInterfaceTemplate> interfaceTemplates;

    ImmutableMap<String, LogicalQueryInterface> interfaceNames;
    ImmutableMap<Long, LogicalAdapter> adapters;
    ImmutableMap<Long, AdapterTemplate> adapterTemplates;

    ImmutableMap<String, LogicalAdapter> adapterNames;

    ImmutableMap<Long, LogicalNamespace> namespaces;

    ImmutableMap<String, LogicalNamespace> namespaceNames;


    public SnapshotImpl( long id, Catalog catalog, Map<Long, LogicalNamespace> namespaces, LogicalRelSnapshot rel, LogicalDocSnapshot doc, LogicalGraphSnapshot graph, AllocSnapshot alloc ) {
        this.id = id;
        this.rel = rel;
        this.doc = doc;
        this.graph = graph;

        this.namespaces = ImmutableMap.copyOf( namespaces );

        this.namespaceNames = ImmutableMap.copyOf( namespaces.values().stream().collect( Collectors.toMap( n -> n.caseSensitive ? n.name : n.name.toLowerCase(), n -> n ) ) );

        this.alloc = alloc;

        this.users = ImmutableMap.copyOf( catalog.getUsers() );
        this.userNames = ImmutableMap.copyOf( users.values().stream().collect( Collectors.toMap( u -> u.name, u -> u ) ) );
        this.interfaces = ImmutableMap.copyOf( catalog.getInterfaces() );
        this.interfaceNames = ImmutableMap.copyOf( interfaces.values().stream().collect( Collectors.toMap( i -> i.name, i -> i ) ) );
        this.interfaceTemplates = ImmutableMap.copyOf( catalog.getInterfaceTemplates() );
        this.adapters = ImmutableMap.copyOf( catalog.getAdapters() );
        this.adapterNames = ImmutableMap.copyOf( adapters.values().stream().collect( Collectors.toMap( a -> a.uniqueName, a -> a ) ) );
        this.adapterTemplates = ImmutableMap.copyOf( catalog.getAdapterTemplates().values().stream().collect( Collectors.toMap( t -> t.id, t -> t ) ) );
    }


    @Override
    public @NotNull List<LogicalNamespace> getNamespaces( @Nullable Pattern name ) {
        if ( name == null ) {
            return namespaces.values().asList();
        }
        return namespaces.values().stream().filter( n -> n.caseSensitive ? n.name.matches( name.toRegex() ) : n.name.toLowerCase().matches( name.toLowerCase().toRegex() ) ).collect( Collectors.toList() );
    }


    @Override
    public @NotNull Optional<LogicalNamespace> getNamespace( long id ) {
        return Optional.ofNullable( namespaces.get( id ) );
    }


    @Override
    public @NotNull Optional<LogicalNamespace> getNamespace( String name ) {
        LogicalNamespace namespace = namespaceNames.get( name );

        if ( namespace != null ) {
            return Optional.of( namespace );
        }
        namespace = namespaceNames.get( name.toLowerCase() );

        if ( namespace != null && !namespace.caseSensitive ) {
            return Optional.of( namespace );
        }

        return Optional.empty();
    }


    @Override
    public @NotNull Optional<LogicalUser> getUser( String name ) {
        return Optional.ofNullable( userNames.get( name ) );
    }


    @Override
    public @NotNull Optional<LogicalUser> getUser( long id ) {
        return Optional.ofNullable( users.get( id ) );
    }


    @Override
    public List<LogicalAdapter> getAdapters() {
        return adapters.values().asList();
    }


    @Override
    public @NotNull Optional<LogicalAdapter> getAdapter( String uniqueName ) {
        return Optional.ofNullable( adapterNames.get( uniqueName ) );
    }


    @Override
    public @NotNull Optional<LogicalAdapter> getAdapter( long id ) {
        return Optional.ofNullable( adapters.get( id ) );
    }


    @Override
    public @NotNull List<LogicalQueryInterface> getQueryInterfaces() {
        return interfaces.values().asList();
    }


    @Override
    public @NotNull Optional<LogicalQueryInterface> getQueryInterface( String uniqueName ) {
        return Optional.ofNullable( interfaceNames.get( uniqueName ) );
    }


    @Override
    public @NotNull Optional<LogicalQueryInterface> getQueryInterface( long id ) {
        return Optional.ofNullable( interfaces.get( id ) );
    }


    @Override
    public @NotNull Optional<QueryInterfaceTemplate> getInterfaceTemplate( String name ) {
        return Optional.ofNullable( interfaceTemplates.get( name ) );
    }


    @Override
    public List<LogicalTable> getTablesForPeriodicProcessing() {
        return null;
    }


    @Override
    public Optional<AdapterTemplate> getAdapterTemplate( long templateId ) {
        return Optional.ofNullable( adapterTemplates.get( templateId ) );
    }


    @Override
    public @NotNull List<AdapterTemplate> getAdapterTemplates() {
        return List.copyOf( adapterTemplates.values() );
    }


    @Override
    public Optional<? extends LogicalEntity> getLogicalEntity( long id ) {
        if ( rel.getTable( id ).isPresent() ) {
            return rel.getTable( id );
        }

        if ( doc.getCollection( id ).isPresent() ) {
            return doc.getCollection( id );
        }

        return graph.getGraph( id );
    }


    @Override
    public @NotNull Optional<AdapterTemplate> getAdapterTemplate( String name, AdapterType adapterType ) {
        return adapterTemplates.values().stream().filter( t -> t.adapterName.equalsIgnoreCase( name ) && t.adapterType == adapterType ).findAny();
    }


    @Override
    public List<AdapterTemplate> getAdapterTemplates( AdapterType adapterType ) {
        return adapterTemplates.values().stream().filter( t -> t.adapterType == adapterType ).collect( Collectors.toList() );
    }


    @Override
    public List<QueryInterfaceTemplate> getInterfaceTemplates() {
        return List.copyOf( interfaceTemplates.values() );
    }


    @Override
    public @NotNull Optional<LogicalEntity> getLogicalEntity( long namespaceId, String entity ) {
        Optional<LogicalTable> optionalTable = rel().getTable( namespaceId, entity );
        if ( optionalTable.isPresent() ) {
            return Optional.of( optionalTable.get() );
        }
        Optional<LogicalCollection> optionalCollection = doc().getCollection( namespaceId, entity );
        if ( optionalCollection.isPresent() ) {
            return Optional.of( optionalCollection.get() );
        }
        Optional<LogicalGraph> optionalGraph = graph().getGraph( namespaceId );
        if ( optionalGraph.isPresent() ) {
            return Optional.of( optionalGraph.get() );
        }
        return Optional.empty();
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }

        SnapshotImpl snapshot = (SnapshotImpl) o;

        if ( !rel.equals( snapshot.rel ) ) {
            return false;
        }
        if ( !doc.equals( snapshot.doc ) ) {
            return false;
        }
        if ( !graph.equals( snapshot.graph ) ) {
            return false;
        }
        if ( !alloc.equals( snapshot.alloc ) ) {
            return false;
        }
        if ( !users.equals( snapshot.users ) ) {
            return false;
        }
        if ( !userNames.equals( snapshot.userNames ) ) {
            return false;
        }
        if ( !interfaces.equals( snapshot.interfaces ) ) {
            return false;
        }
        if ( !interfaceTemplates.equals( snapshot.interfaceTemplates ) ) {
            return false;
        }
        if ( !interfaceNames.equals( snapshot.interfaceNames ) ) {
            return false;
        }
        if ( !adapters.equals( snapshot.adapters ) ) {
            return false;
        }
        if ( !adapterTemplates.equals( snapshot.adapterTemplates ) ) {
            return false;
        }
        if ( !adapterNames.equals( snapshot.adapterNames ) ) {
            return false;
        }
        if ( !namespaces.equals( snapshot.namespaces ) ) {
            return false;
        }
        return namespaceNames.equals( snapshot.namespaceNames );
    }


    @Override
    public int hashCode() {
        int result = rel.hashCode();
        result = 31 * result + doc.hashCode();
        result = 31 * result + graph.hashCode();
        result = 31 * result + alloc.hashCode();
        result = 31 * result + (int) (id ^ (id >>> 32));
        result = 31 * result + users.hashCode();
        result = 31 * result + userNames.hashCode();
        result = 31 * result + interfaces.hashCode();
        result = 31 * result + interfaceTemplates.hashCode();
        result = 31 * result + interfaceNames.hashCode();
        result = 31 * result + adapters.hashCode();
        result = 31 * result + adapterTemplates.hashCode();
        result = 31 * result + adapterNames.hashCode();
        result = 31 * result + namespaces.hashCode();
        result = 31 * result + namespaceNames.hashCode();
        return result;
    }

}