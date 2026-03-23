import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.NextMsg
import com.mentalresonance.dust.core.msgs.StartMsg
import groovy.util.logging.Slf4j
import com.mentalresonance.dust.core.msgs.Terminated
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

/**
 * Fire up a ping/ack situation between two remotes but have one stop and restart its ActorSystem.
 * Things should recover.
 * Too actors ping back and forth and keep track of counts. But one Actor only has 1/2 the counts so it's
 * ActorSystem is stopped by its partner. The Actor system is then restarted as is the Actor and pinging back and forth
 * should continue until all the pings are complete.
 */
@Slf4j
class RemoteGlitch extends Specification {

	public static ActorSystem me, sys1, sys2

	public static success = false

	public static ActorRef ping1Ref, ping2Ref

	@Slf4j
	static class Pinger extends Actor {

		Integer count = 0

		static Props props(int count) {
			Props.create(Pinger, count)
		}

		Pinger(int count) {
			this.count = count
		}

		void preStart() {
			log.info "${self.path} created with count=$count"
		}

		@Override
		ActorBehavior createBehavior() {
			(message) -> {
				switch (message) {
					case StartMsg:
						if (((0 == ((StartMsg)message).msg()))) {
							sender.context.system.stop()
							scheduleIn(new NextMsg(), 1000)
						}
						else {
							sleep(10)
							if (sender.tell(new StartMsg(--count), self))
								log.info "${self.path} ping'd"
							else {
								log.info "${self.path} ping failed"
								++count
							}
						}
						break

					case NextMsg:
						log.info "Restarting Pinger 2"
						sys2 = new ActorSystemBuilder().name("sys2").port(9092).build()
						ping2Ref = sys2.context.actorOf( Pinger.props(50), "p2")
						ping2Ref.tell(new StartMsg(--count), self)
						break

					default:
						log.error "???"
				}
			}
		}
	}

	@Slf4j
	static class Runner extends Actor {


		static Props props() { Props.create(Runner) }

		void preStart() {

			ping1Ref = watch(sys1.context.actorOf( Pinger.props(50), "p1"))
			ping2Ref = watch(sys2.context.actorOf( Pinger.props(25), "p2"))

			ping1Ref.tell(new StartMsg(), ping2Ref)
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case Terminated:
						if (sender == ping1Ref)
							stopSelf()
					break
				}
			}
		}
	}

	def "Remote Glitch"() {
		when:
			me = new ActorSystemBuilder().name("Me").port(9090).build()
			sys1 = new ActorSystemBuilder().name("sys1").port(9091).build()
			sys2 = new ActorSystemBuilder().name("sys2").port(9092).build()

			me.context.actorOf(Runner.props()).waitForDeath()
		/**
		 * Ping2 will stop before Ping one -- ping 1 should start retrying
		 */
			success = true
		then:
			success
	}

}
