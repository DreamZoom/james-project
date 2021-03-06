/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.imap.decode;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Charsets;
import org.apache.james.protocols.imap.DecodingException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ImapRequestLineReaderTest {

    private final OutputStream outputStream = null;
    private InputStream inputStream;
    private ImapRequestStreamLineReader lineReader;

    @Test
    public void nextNonSpaceCharShouldReturnTheFirstCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("anyString \n").getBytes(Charsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test
    public void nextNonSpaceCharShouldIgnoreTheSpaceAndReturnTheFirstNonSpaceCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("    anyString \n").getBytes(Charsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test(expected = DecodingException.class)
    public void nextNonSpaceCharShouldThrowExceptionWhenNotFound() throws Exception {
        inputStream = new ByteArrayInputStream(("    ").getBytes(Charsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        lineReader.nextNonSpaceChar();
    }
}