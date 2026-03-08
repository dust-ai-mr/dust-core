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
import com.mentalresonance.dust.core.actors.SentMessage;
import com.mentalresonance.dust.core.msgs.*;
import org.apache.fory.Fory;
import org.apache.fory.config.Language;


/**
 * (de) serialize objects using the  Apache Fory serializer
 *
 * @author alanl
 */
public class ForyService {

    /**
     * Constructor
     */
    private ForyService() {}

    public static Fory fory() {
        Fory fory = Fory.builder().withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .build();


        fory.register(ActorRef.class);
        fory.register(SentMessage.class);
        fory.register(DeadLetter.class);
        fory.register(ReturnableMsg.class);
        fory.register(CreateChildMsg.class);
        fory.register(GetStateMsg.class);
        fory.register(GetChildrenMsg.class);
        fory.register(PingMsg.class);

        return fory;
    }
}
