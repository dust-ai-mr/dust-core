import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.LogActor
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

@Slf4j
class GetSystemActors extends Specification {

	public static success = false

	def "Get System Actors"() {
		when:
			ActorSystem system = new ActorSystem("Get System Actors")
			success = system.context.getDeadLetterActor() && system.context.getUserActor()
			system.stop()
		then:
			success
	}
}
