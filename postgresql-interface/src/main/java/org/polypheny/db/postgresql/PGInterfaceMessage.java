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

package org.polypheny.db.postgresql;

//message sent by client and server (connection-level)
public class PGInterfaceMessage {

    private PGInterfaceHeaders header;
    private String msgBody;
    private int length; //default is 4, if a different length is mentioned in protocol, this is given
    private boolean defaultLength;
    private static final char delimiter = '§'; //for the subparts
    private PGInterfaceErrorHandler errorHandler;


    /**
     * Creates a PG-Message. It contains all relevant information to send a message to the client
     * @param header What header should be sent (depends on message-type)
     * @param msgBody The message itself
     * @param length The length of the message (the length itself is included)
     * @param defaultLength The length of the length sent (which is included). Length is 4
     */
    public PGInterfaceMessage( PGInterfaceHeaders header, String msgBody, int length, boolean defaultLength ) {
        this.header = header;
        this.msgBody = msgBody;
        this.length = length;
        this.defaultLength = defaultLength;
    }


    public PGInterfaceHeaders getHeader() {
        return this.header;
    }


    public char getHeaderChar() {

        //if header is a single character
        if ( header != PGInterfaceHeaders.ONE && header != PGInterfaceHeaders.TWO && header != PGInterfaceHeaders.THREE ) {
            String headerString = header.toString();
            return headerString.charAt( 0 );
        }
        //if header is a number
        //TODO(FF): make a nicer version of this... if you cast headerInt to char directly it returns '\u0001' and not '1'
        else {
            int headerInt = getHeaderInt();
            if ( headerInt == 1 ) {
                return '1';
            } else if ( headerInt == 2 ) {
                return '2';
            } else if ( headerInt == 3 ) {
                return '3';
            }
        }
        //TODO(FF): if returns 0, something went wrong --> inform client (how??)

        return 0;
    }


    public int getHeaderInt() {
        String headerString = header.toString();
        if ( headerString.equals( "ONE" ) ) {
            return 1;
        } else if ( headerString.equals( "TWO" ) ) {
            return 2;
        } else if ( headerString.equals( "THREE" ) ) {
            return 3;
        }
        //TODO(FF): if returns 0, something went wrong (how to inform client??)
        //TODO(FF): if returns 0, something went wrong (how to inform client??)
        return 0;
    }


    public void setHeader( PGInterfaceHeaders header ) {
        this.header = header;
    }


    public int getLength() {
        return this.length;
    }


    public void setLength( int length ) {
        this.length = length;
    }


    public void setDefaultLength( boolean val ) {
        this.defaultLength = val;
    }


    public boolean isDefaultLength() {
        return this.defaultLength;
    }


    public String getMsgBody() {
        return msgBody;
    }


    public void setMsgBody( String msgBody ) {
        this.msgBody = msgBody;
    }


    /**
     * Gets the different sub-parts of a message
     *
     * @param part the index of the requested part(s), starting at 0
     * @return a string array with each requested part
     */
    public String[] getMsgPart( int[] part ) {
        String subStrings[] = msgBody.split( getDelimiter() );
        String result[] = new String[part.length];

        for ( int i = 0; i < (part.length); i++ ) {
            result[i] = subStrings[i];
        }
        return result;
    }


    /**
     * Get what delimiter is currently set
     * @return the current delimiter as string
     */
    public static String getDelimiter() {
        String del = String.valueOf( delimiter );
        return del;
    }

}
