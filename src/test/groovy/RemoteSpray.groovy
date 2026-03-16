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

import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.actors.lib.NullActor
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Upper limit on message rate to a single remote Actor
 */

class RemoteSpray extends Specification {


	static ActorSystem sys1 = new ActorSystem("sys1", 9096)
	static ActorSystem me = new ActorSystem("sys3", 9099)

	static success = true

	@Slf4j
	static class Runner extends Actor {
		int shots, deaths=0

		static Props props(int shots) {
			Props.create(Runner, shots)
		}

		Runner(int shots) {
			this.shots = shots
		}

		void preStart() {
			sys1.context.actorOf( NullActor.props(), "s1")

			ActorRef a1Ref = watch(sys1.context.actorSelection("dust://localhost:9096/sys1/user/s1"))

			(1..shots).each {
				a1Ref.tell(new StartMsg(), self)
			}
			a1Ref.tell(new PoisonPill(), self)
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case Terminated:
							stopSelf()
						break

					default: log.error "????"
				}
			}
		}
	}


	def "Remote Spray"() {
		when:
			def clip = 2000000

			long time = System.currentTimeMillis()

			me.context.actorOf(Runner.props(clip)).waitForDeath()

			time = System.currentTimeMillis() - time
			log.info "Finished: ${clip} in $time ms (${1000.0f*clip / time} msgs / sec)"
			if (success) log.info "Success !!"
			me.stop()
			sys1.stop()
		then:
			success
	}
}
