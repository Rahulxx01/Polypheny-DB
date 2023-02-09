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

public class PGInterfaceMessage {

    private PGInterfaceHeaders header;
    private String msgBody;
    private int length; //default is 4, if a different length is mentioned in protocol, this is given
    private int sizeMsgBody; //how many subparts are in the message. seperated by delimiter
    private final char delimiter = '§';

    public PGInterfaceMessage(PGInterfaceHeaders header, String msgBody, int length, int sizeMsgBody) {
        this.header = header;
        this.msgBody = msgBody;
        this.length = length;
        this.sizeMsgBody = sizeMsgBody;
    }

    public PGInterfaceHeaders getHeader() {
        return this.header;
    }

    public void setHeader(PGInterfaceHeaders header) {
        this.header = header;
    }

    public int getLength() {
        return this.length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getMsgBody() {
        return msgBody;
    }

    public void setMsgBody(String msgBody) {
        this.msgBody = msgBody;
    }



    /**
     * gets the different subparts of a message
     * @param part the index of the requested part(s), starting at 0
     * @return a string array with each requested part
     */
    public String[] getMsgPart(int[] part) {
        String subStrings[] = msgBody.split("§");
        String result[] = new String[0];

        for (int i=0; i<(part.length); i++) {
            result[i] = subStrings[i];
        }
        return result;
    }

}
