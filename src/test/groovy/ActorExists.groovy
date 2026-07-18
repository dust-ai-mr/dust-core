import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.ActorSystemBuilder
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.LogActor
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
class ActorExists extends Specification {

	public static exists = false, notExists = false

	@Slf4j
	static class Child0 extends Actor {

		static Props props() {
			Props.create(Child0)
		}

		@Override
		void preStart() {
			actorOf(Child.props(), 'child')
			exists = actorExists('/user/child0/child')
			log.info "Exists=$exists"
			notExists = ! actorExists('/user/child0/child2')
			log.info "NotExists=$notExists"
			stopSelf()
		}
	}

	@Slf4j
	static class Child extends Actor {

		static Props props() {
			Props.create(Child)
		}
	}

	def "Actor Exists"() {
		when:
			log.info ">>>>>>>>>>> Actor Exists"
			ActorSystem system = new ActorSystemBuilder().name("Test").build()
			system.context.actorOf(Child0.props(), 'child0')
			Thread.sleep(500)
			system.stop()
		then:
			exists && notExists
	}
}
