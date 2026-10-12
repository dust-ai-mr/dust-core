/*
 *
 *  Copyright 2024-2025 Alan Littleford
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package com.mentalresonance.dust.core.net;

import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.PoisonPill;
import com.mentalresonance.dust.core.actors.SentMessage;
import com.mentalresonance.dust.core.msgs.*;
import org.apache.fory.Fory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.Language;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.logging.LogLevel;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * (de) serialize objects using the  Apache Fory serializer
 *
 * @author alanl
 */


public class ForyService {
    /**
     * Constructor
     */
    private ForyService() { }

    public static Fory fory() {
        return fory(new LinkedList<>());
    }
    public static Fory fory(List<Class<?>> classes) {
        // Force Fory's internal global level to ERROR -- way.... too chatty otherwise
        // This affects ForyLogger's internal "if (LoggerFactory.getLogLevel() >= ...)" checks
        LoggerFactory.setLogLevel(LogLevel.ERROR_LEVEL);
        Fory fory = Fory.builder().withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .withRefTracking(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withClassVersionCheck(true)
            // .withCodegen(false)
            .build();


        fory.register(ActorRef.class, 100);
        fory.register(SentMessage.class, 101);
        fory.register(DeadLetter.class, 102);
        fory.register(ReturnableMsg.class, 103);
        fory.register(CreateChildMsg.class, 104);
        fory.register(GetStateMsg.class, 105);
        fory.register(GetChildrenMsg.class, 106);
        fory.register(PingMsg.class, 107);
        fory.register(PoisonPill.class, 108);
        fory.register(Terminated.class, 109);


        for(Class<?> clazz : classes) {
            fory.register(clazz);
        }
        return fory;
    }
}
