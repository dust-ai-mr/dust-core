import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.PipelineActor
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Sets up a pipeline of identical Actors and passes a message in whose counter will get incremented by 1 at each
 * stage of the pipeline. Since the message we sent in is not a returnable message the pipeline will 'drop' the message
 * after the final stage processes it.
 */
@CompileStatic
class PipelineTest extends Specification {

	@Slf4j
	@CompileStatic
	static class Add1Actor extends Actor {

		static Props props() {
			Props.create(Add1Actor)
		}

		Add1Actor() {}

		@Override
		ActorBehavior createBehavior() {
			(message) -> {
				switch (message) {
					case Add1Msg:
						Add1Msg msg = (Add1Msg) message
						log.info "${self.path}: Count = ${++msg.counter}"
						// My parent is the pipe - it will pass the message on to the next stage
						parent.tell(msg, self);
						break

					default:
						log.warn "Unexpected message $message"
				}
			}
		}
	}

	@CompileStatic
	// The message which is sent down the pipe
	static class Add1Msg implements Serializable {
		int counter = 0
	}

	int counter

	def "Pipeline"() {

		when:
			ActorSystem system = new ActorSystem("Test")
			Add1Msg msg = new Add1Msg()
			List<Props> props = []
			List<String> names = []

			/*
			  Set up list of Actors and their names
			 */
			(1..4).each {
				props << Add1Actor.props()
				names << "stage$it".toString()
			}
			// And create the pipeline
			ActorRef pipe = system.context.actorOf(PipelineActor.props(props, names))
			// Send the pipeline a message to process
			pipe.tell(msg, null)
			Thread.sleep(500L)
			counter = msg.counter
			system.stop()
					then:
			counter == 4
	}

}
