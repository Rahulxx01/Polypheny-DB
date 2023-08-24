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

package org.polypheny.db.exploreByExample.requests;


import org.polypheny.db.webui.models.UiColumnDefinition;
import org.polypheny.db.webui.models.requests.UIRequest;


public class ClassifyAllData extends UIRequest {

    public Integer id;
    public UiColumnDefinition[] header;
    public String[][] classified;
    /**
     * TRUE if information about the query execution should be added to the Query Analyzer (InformationManager)
     */
    public boolean analyze;
    public int cPage;

}
