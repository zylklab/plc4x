/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.java.canopen;

import org.apache.plc4x.java.canopen.transport.CANOpenFrame;
import org.apache.plc4x.java.canopen.transport.CANTransport;
import org.apache.plc4x.java.canopen.transport.socketcan.io.CANOpenSocketCANFrameIO;
import org.apache.plc4x.java.spi.configuration.Configuration;
import org.apache.plc4x.java.spi.generation.MessageIO;
import org.apache.plc4x.java.transport.test.TestTransport;

public class CANTestTransport extends TestTransport implements CANTransport {

    @Override
    public MessageIO<CANOpenFrame, CANOpenFrame> getMessageIO(Configuration cfg) {
        return new CANOpenSocketCANFrameIO();
    }

    @Override
    public Class<CANOpenFrame> getMessageType() {
        return CANOpenFrame.class;
    }
}