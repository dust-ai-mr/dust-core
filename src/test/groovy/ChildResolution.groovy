import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.PoisonPill
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.LogActor
import groovy.util.logging.Slf4j
import spock.lang.Specification

/*
 *
 *  Copyright 2024 Alan Littleford
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
class ChildResolution extends Specification {

	@Slf4j
	static class Child0 extends Actor {

		static Props props() {
			Props.create(Child0)
		}

		@Override
		void preStart() {
			actorOf(Child.props(), 'child')
			log.info "Started child"
			actorSelection("./child/logger").tell("Log me", self)
		}
	}

	@Slf4j
	static class Child extends Actor {

		static Props props() {
			Props.create(Child)
		}

		@Override
		void preStart() {
			actorOf(LogActor.props(), 'logger')
			log.info "Started logger"
		}
	}

	def "Child Resolution"() {
		when:
			log.info "Starting path test locally"
			ActorSystem system = new ActorSystem("Test")

			system.context.actorOf(Child0.props(), 'child0')
			Thread.sleep(15000L)
			system.stop()
		then:
			true
	}
}
