import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.NextMsg
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.Terminated
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

/**
 * One Actor pings another using ActorRef obtained by ActorSelection.
 */
@Slf4j
class RemoteGlitch2 extends Specification {

	public static ActorSystem me, sys1, sys2

	public static success = false

	public static ActorRef ping1Ref, ping2Ref

	public static String lastSender

	@Slf4j
	static class Pinger extends Actor {

		Integer count = 0
		ActorRef other

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
						log.info "${self.path} got start"
						other = other ?: sender
						if (((0 == ((StartMsg)message).msg()))) {
							log.info "$self is Stopping $sender"
							sender.tell(new PoisonPill(), self)
							stopSelf()
						}
						else {
							Thread.sleep(1000)
							if (sender.tell(new StartMsg(--count), self)) {
								lastSender = self.name
								log.info "${self.path} ping'd $sender"
							}
							else {
								log.info "${self.path} ping failed"
								++count
							}
						}
						break

					default:
						log.error " ${self.path} got ??? from $sender"
				}
			}
		}
	}

	@Slf4j
	static class Runner extends Actor {


		static Props props() { Props.create(Runner) }

		void preStart() {

			ping1Ref = watch(sys1.context.actorSelection( "dust://localhost:9091/sys1/user/p1"))
			ping2Ref = watch(sys2.context.actorSelection( "dust://localhost:9092/sys2/user/p2"))
			//sys2.context.stop()
			ping1Ref.tell(new StartMsg(50), ping2Ref)
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case Terminated:
					break
				}
			}
		}
	}

	def "Remote Glitch2"() {
		when:
			me = new ActorSystemBuilder().name("Me").port(9090).build()

			sys1 = new ActorSystemBuilder().name("sys1").port(9091).build()
			sys1.context.actorOf( Pinger.props(100), "p1")

			sys2 = new ActorSystemBuilder().name("sys2").port(9092).build()
			sys2.context.actorOf( Pinger.props(50), "p2")
			Thread.start {
				sleep(500)
				while (lastSender != "p1") Thread.sleep(10)
				sys1.context.stop()
				sleep(30000)
				sys1 = new ActorSystemBuilder().name("sys1").port(9091).build()
				sys1.context.actorOf( Pinger.props(100), "p1")
				ping1Ref = sys1.context.actorSelection( "dust://localhost:9091/sys1/user/p1")
				ping1Ref.tell(new StartMsg(50), ping2Ref)
			}
			me.context.actorOf(Runner.props()).waitForDeath()

		/**
		 * Ping2 will stop before Ping one -- ping 1 should start retrying
		 */
			success = true
		then:
			success
	}

}
