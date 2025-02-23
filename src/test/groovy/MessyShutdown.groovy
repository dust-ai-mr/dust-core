import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.StartMsg
import groovy.util.logging.Slf4j
import spock.lang.Specification

/*
 *
 *  Copyright 2024-Present Alan Littleford
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

@Slf4j
class MessyShutdown extends Specification {

    public static success = false

    @Slf4j
    static class Child extends Actor {

        static Props props() {
            Props.create(Child)
        }

        @Override
        void postStop() {
            log.info "Messy stop child ${self.isException}"
            if (self.isException instanceof ParentException) {
                success = true
                log.info "My parent died, Clyde: ${((ParentException)self.isException).cause}"
            }
        }
    }

    @Slf4j
    static class Parent extends Actor {

        static Props props() {
            Props.create(Parent)
        }

        void preStart() {
            actorOf(Child.props())
        }

        @Override
        ActorBehavior createBehavior() {
            (Serializable message) -> {
                switch (message) {
                    case StartMsg:
                        throw new Exception("I'm dead, Fred")
                        break

                    default: log.error "$message"
                }
            }
        }
        @Override
        void postStop() {
            log.info "Messy stop parent ${self.isException}"
            success = self.isException != null
        }
    }

    def "Dead Letter"() {
        when:
            ActorSystem system = new ActorSystem("MessyShutdown")
            ActorRef stopper = system.context.actorOf( Parent.props())

            stopper.tell(new StartMsg(), null)
            stopper.waitForDeath()
        then:
            success
    }

}
