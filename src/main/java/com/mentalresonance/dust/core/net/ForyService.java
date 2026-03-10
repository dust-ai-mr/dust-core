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
import org.apache.fory.config.Language;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.logging.LogLevel;

/**
 * (de) serialize objects using the  Apache Fory serializer
 *
 * @author alanl
 */


public class ForyService {
    /**
     * Constructor
     */
    private ForyService() {
    }

    public static Fory fory() {
        // Force Fory's internal global level to ERROR -- way.... too chatty otherwise
        // This affects ForyLogger's internal "if (LoggerFactory.getLogLevel() >= ...)" checks
        LoggerFactory.setLogLevel(LogLevel.ERROR_LEVEL);
        Fory fory = Fory.builder().withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .build();


        fory.register(ActorRef.class, 101);
        fory.register(SentMessage.class, 102);
        fory.register(DeadLetter.class, 103);
        fory.register(ReturnableMsg.class, 104);
        fory.register(CreateChildMsg.class, 105);
        fory.register(GetStateMsg.class, 106);
        fory.register(GetChildrenMsg.class, 107);
        fory.register(PingMsg.class, 108);
        fory.register(PoisonPill.class, 109);

        return fory;
    }
}
