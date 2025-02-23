import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import groovy.util.logging.Slf4j
import spock.lang.Specification
import com.mentalresonance.dust.core.msgs.StartMsg

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
	class CleanShutdown extends Specification {

		public static success = false

		@Slf4j
		static class Stopper extends Actor {

			static Props props() {
				Props.create(Stopper)
			}

			Stopper() {}

			@Override
			ActorBehavior createBehavior() {
				(message) -> {
					switch (message) {
						case StartMsg:
							log.info "Sleeping for 10 seconds"
							safeSleep(10000L)
							break
					}
				}
			}
			@Override
			void postStop() {
				log.info "Clean stop"
				success = true
			}
		}

		def "Dead Letter"() {
			when:
				ActorSystem system = new ActorSystem("CleanShutdown")
				ActorRef stopper = system.context.actorOf( Stopper.props())
				stopper.tell(new StartMsg(), null)
				Thread.sleep(2000)
				system.context.stop(stopper)
				Thread.sleep(1000)
			then:
				success
		}

	}
