import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.ServiceManagerActor
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import spock.lang.Specification

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

/**
 * Demonstrate use of a Service actor to manage a pool of identical Actors. Here the Service actors
 * simply take a message containing an integer and return the message with that integer's square in it.
 *
 * This is not a very practical example - usually a ServiceManager is used to restrict access to a limited
 * resource - e.g. a web site or some such but it shows how the ServiceManager/ServiceActor pairing works.
 *
 * Two things to note: when a ServiceActor has done its thing it kills itself. Also it replies to the sender
 * using its parent as a pseudonym. This is because the ultimate client of this Actor used the ServiceManager (its parent)
 * as the target, and so if it is expecting a response it expects it from the ServiceManagerActor (the implementing
 * Actor having died).
 *
 * So all in all this test involves the creation / destruction of limit Actors and the sending of 2*limit messages
 * (in this case 1000000 Actors and 2000000 messages) with no more than 10 Actors active at a time (excluding scaffolding).
 */
@Slf4j
@CompileStatic
class ServiceActorTest extends Specification {

	static class SquareMsg implements Serializable {
		int number, square
	}

	@Slf4j
	@CompileStatic
	/*
	 The Service actor. Square one number, reply, die.
	 */
	static class SquareServiceActor extends Actor {

		static Props props() {
			Props.create(SquareServiceActor)
		}

		SquareServiceActor() {}

		@Override
		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case SquareMsg:
						SquareMsg msg = (SquareMsg)message
						msg.square = msg.number * msg.number
						sender.tell(msg, parent) // Reply to sender as though I were my parent
						stopSelf() // And then stop
						break

					default:
						log.info "Unexpected message $message"
				}
			}
		}

	}

	@Slf4j
	@CompileStatic
	/*
	   Actor to track progress of squarings. It creates the appropriate service manager and sends it the requests.
	   Once it has accumulated all the results we stop.
	 */
	static class SquareTestActor extends Actor {

		Map<Integer, Integer> results = [:]
		int limit

		ActorRef squareRef

		static Props props(int limit) {
			Props.create(SquareTestActor, limit)
		}

		SquareTestActor(int limit) {
			this.limit = limit
		}

		@Override
		void preStart() {
			// Create the service manager allowing at most 10 service actors at any given time
			squareRef = actorOf(ServiceManagerActor.props(SquareServiceActor.props(), 10), 'square')
			// Tell it to square the firs limit integers
			(1..limit).each {
				squareRef.tell(new SquareMsg(number: it), self)
			}
		}

		@Override
		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case SquareMsg:
						// Response from ServiceManagerActor with our squre
						SquareMsg msg = (SquareMsg)message

						results[msg.number] = msg.square

						if (results.size() == limit) {
							log.info "Got $limit squares back"
							stopSelf()
						}
						break

					default:
						log.info "Unexpected message $message"
				}
			}
		}
	}

	def "ServiceActor Test"() {
		when:
			ActorSystem system = new ActorSystem("ServiceActorTest")
			log.info "Starting Squares"
			system.context.actorOf( SquareTestActor.props(1000000), "squares").waitForDeath()
			system.stop()
					then:
			true
	}
}
