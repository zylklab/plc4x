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

package org.apache.plc4x.nifi.subscription;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.messages.DefaultPlcSubscriptionEvent;
import org.apache.plc4x.java.spi.messages.utils.ResponseItem;

public class Plc4xSubscriptionEvent {
    private String triggerTag;
    private Instant timestamp;
    private DefaultPlcSubscriptionEvent event;
    private Map<String, String> tags;
    private Plc4xSubscriptionResponseType responseType;
    

    public Plc4xSubscriptionEvent(String triggerTag, DefaultPlcSubscriptionEvent event, Map<String, String> tags,
            Plc4xSubscriptionResponseType responseType) {
        this.triggerTag = triggerTag;
        this.event = event;
        this.responseType = responseType;
        this.timestamp = event.getTimestamp();
        this.tags = tags;
    }

    public String getTriggerTag() {
        return triggerTag;
    }

    public void setTriggerTag(String triggerTag) {
        this.triggerTag = triggerTag;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public DefaultPlcSubscriptionEvent getEvent() {
        if (responseType.equals(Plc4xSubscriptionResponseType.SINGLE)){
            Map<String, ResponseItem<PlcValue>> tmpTags = new HashMap<>();
            if (!event.getValues().containsKey(triggerTag))
                return null;
            tmpTags.put(triggerTag, new ResponseItem<>(event.getResponseCode(triggerTag), event.getPlcValue(triggerTag)));
            return new DefaultPlcSubscriptionEvent(timestamp, tmpTags);
        }
        return event;
    }

    public void setEvent(DefaultPlcSubscriptionEvent event) {
        this.event = event;
    }

    public Map<String,String> getTagsMap() {
        Map<String, String> tmpTags = new HashMap<>();
        if (responseType.equals(Plc4xSubscriptionResponseType.SINGLE)){
            tmpTags.put(triggerTag, tags.get(triggerTag));
        } else{
            return tags;
        }
        return tmpTags;
    }

    public Plc4xSubscriptionResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(Plc4xSubscriptionResponseType responseType) {
        this.responseType = responseType;
    }
    
}
