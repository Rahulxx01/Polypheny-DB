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

package org.polypheny.db.adaptimizer.suite;

import java.io.Serializable;

public abstract class SuiteComm implements Serializable {

    // Response Codes ----------------------------------
    public static final String OK_CODE = "000000";
    public static final String ERROR_CODE = "000001";

    // -------------------------------------------------


    // Request Codes -----------------------------------
    public static final String QUERY_CODE = "000002";


    // -------------------------------------------------

    protected String code;

    protected String message;

    public String getCode() {
        return this.code;
    }

    public String getMessage() {
        return message;
    }

}
