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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Event;

import java.io.Serializable;

@EqualsAndHashCode
public final class CatalogTrigger implements CatalogEntity, Restorable {

    private static final long serialVersionUID = -4752365450652498995L;
    private final Long schemaId;
    private final String name;
    private final Event event;
    private final long databaseId;
    
    private final long triggerId;

    private final long tableId;

    @Getter
    private final Catalog.QueryLanguage language;
    private final String query;

    public CatalogTrigger(Long schemaId, String name, Long databaseId, Long triggerId, Event event, long tableId, Catalog.QueryLanguage language, String query) {
        this.name = name;
        this.schemaId = schemaId;
        this.databaseId = databaseId;
        this.event = event;
        this.tableId = tableId;
        this.language = language;
        this.query = query;
        this.triggerId = triggerId;
    }

    @Override
    public Serializable[] getParameterArray() {
        // throw UOE because should not be used.
        throw new UnsupportedOperationException();
    }

    @SneakyThrows
    public String getDatabaseName() {
        return Catalog.getInstance().getDatabase(databaseId).name;
    }

    @SneakyThrows
    public String getSchemaName() {
        return Catalog.getInstance().getSchema(schemaId).name;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return triggerId;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public Catalog.SchemaType getSchemaType() {
        return Catalog.getInstance().getSchema( schemaId ).schemaType;
    }

    public long getTriggerId() {
        return triggerId;
    }

    public long getTableId() {
        return tableId;
    }

    public Event getEvent() {
        return event;
    }

    public AlgNode getDefinition() {
        AlgNode triggerQuery = Catalog.getInstance().getTriggerNodes().get(triggerId);
        if(triggerQuery == null) {
            throw new RuntimeException("No definition for trigger query found");
        }
        return triggerQuery;
    }
}
