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

package org.polypheny.db.replication.freshness.exceptions;


import org.polypheny.db.replication.freshness.FreshnessManager.EvaluationType;


public class UnsupportedFreshnessSpecificationRuntimeException extends FreshnessRuntimeException {

    public UnsupportedFreshnessSpecificationRuntimeException( EvaluationType evaluationType, final String freshnessValue ) {
        super( "The specified tolerated level of freshness: '" + freshnessValue + "' cannot be used for the "
                + "associated evaluation type: '" + evaluationType.toString() + "'." );
    }
}