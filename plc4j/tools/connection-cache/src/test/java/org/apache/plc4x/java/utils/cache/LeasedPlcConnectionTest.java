/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.java.utils.cache;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LeasedPlcConnectionTest {
    
    /**
     * This test tries to read and encounters a TimeoutException. The connection should be marked as invalidated
     * to be removed from the cache.
     */
    @Test
    public void testTimeoutInvalidatesConnection() {
        PlcConnection innerConnection = Mockito.mock(PlcConnection.class);
        PlcReadRequest.Builder builder = Mockito.mock(PlcReadRequest.Builder.class);
        PlcReadRequest innerRequest = Mockito.mock(PlcReadRequest.class);
        ConnectionContainer container = Mockito.mock(ConnectionContainer.class);

        Mockito.when(innerRequest.execute()).thenReturn(CompletableFuture.supplyAsync(() -> {
            for (int i = 0; i < 50; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) { }
            }
            return null;
        }));


        Mockito.when(builder.addTagAddress(any(), any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(innerRequest);
        Mockito.when(innerConnection.readRequestBuilder()).thenReturn(builder);

        
        try (final LeasedPlcConnection connection = new LeasedPlcConnection(container, innerConnection, Duration.ofMinutes(1000L))) {
            PlcReadRequest request = connection.readRequestBuilder().build();


            try {
                request.execute().get(50, TimeUnit.MILLISECONDS);
                Assertions.fail("Was expecting an exception here");
            } catch (Exception e) { 
                Assertions.assertInstanceOf(TimeoutException.class, e);
            }

            // After the timeout the connection should be invalidated
            Assertions.assertTrue(connection.isInvalidateConnection());
        }
    }

}
