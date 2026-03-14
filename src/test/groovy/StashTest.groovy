import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
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

/*
	Create an Actor which flips to some stashing behavior, flips back and confirms it can unstash
	and process its 'missed' messages
 */
class StashTest extends Specification {

	public static success = false, gotmsg = false, got10 = false
	public static MSG = 'msg'

	static class Stasher extends Actor {

		def unstashed = []

		static Props props() {
			Props.create(Stasher)
		}

		Stasher() {}

		@Override
		ActorBehavior createBehavior() {
			(message) -> {
				switch (message) {
					case StartMsg:
						become(stashBehavior())
						break

					case StopMsg:
						// Not only must we have received all 10 integers but the contract preserves message ordering
						got10 = unstashed == [1,2,3,4,5,6,7,8,9,10]
						stopSelf()
						break

					default: unstashed << message
				}
			}
		}

		ActorBehavior stashBehavior() {
			(message) -> {
				switch(message) {
					case MSG:
						gotmsg = true
						break

					case StopMsg:
						become(createBehavior())
						unstashAll()
						break

					default:
						stash(message)
				}
			}
		}

		@Override
		void postStop() {
			success = true
		}
	}

	def "Stash"() {
		when:
			ActorSystem system = new ActorSystem("Stash")
			ActorRef stasher = system.context.actorOf( Stasher.props())
			// Change behavior to stashing message ..
			stasher.tell(new StartMsg(), null)
			// Except this one which sets a flag ..
			stasher.tell(MSG, null)
			// But these get stashed
			(1..10).each { stasher.tell(Integer.valueOf(it), null)}
			// Until we tell it to revert to original behavior and unstash any messages
			stasher.tell(new StopMsg(), null)
			// And then we check what we unstahsed and finish
			stasher.tell(new StopMsg(), null)
			stasher.waitForDeath()

		then:
			success
			gotmsg
			got10
	}

}
