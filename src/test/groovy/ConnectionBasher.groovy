import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.actors.lib.NullActor
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Create loads of connections and talk down them. This should cause churn and reuse on both ends of the connection
 * which should be handled gracefully by the remote server and client.
 */

class ConnectionBasher extends Specification {


	static ActorSystem sys1 = new ActorSystemBuilder().name("me").port(9096).build()
	static ActorSystem sys2 = new ActorSystemBuilder().name("remote").port(9097).build()

	static success = true

	@Slf4j
	static class Child extends Actor {
		static Props props() {
			Props.create(Child)
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				// log.info "${self.path} got $message"
				if (message instanceof StopMsg)
					stopSelf()
			}
		}
	}
	@Slf4j
	static class Parent extends Actor {
		int kids

		static Props props(int kids) {
			Props.create(Parent, kids)
		}

		Parent(int kids) {
			this.kids = kids
		}

		void preStart() {
			(1..kids).each {
				watch(actorOf(Child.props(), "$it"))
			}
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case Terminated:
						if (0 == children.size()) {
							log.info "All children gone .."
							stopSelf()
						}
						break

					default: log.info "???"
				}
			}
		}
	}

	@Slf4j
	static class Runner extends Actor {
		int kids
		List<ActorRef> refs = []

		static Props props(int kids) {
			Props.create(Runner, kids)
		}

		Runner(int kids) {
			this.kids = kids
		}

		void preStart() {
			sys2.context.actorOf( Parent.props(kids), "parent")

			// Parent dies when all of its children are gone, then we die
			watch(sys2.context.actorSelection("dust://localhost:9097/sys2/user/parent/"))

			(1..kids).each {
				refs << sys2.context.actorSelection("dust://localhost:9097/sys2/user/parent/$it")
			}
			refs.each {
				it.tell(new StartMsg(), self)
			}
			refs.each {
				it.tell(new StopMsg(), self)
			}
		}

		ActorBehavior createBehavior() {
			(message) -> {
				if (message instanceof Terminated)
					stopSelf()
			}
		}
	}


	def "Connection Basher"() {
		when:
			def clip = 512
			log.info "Starting"
			sys1.context.actorOf(Runner.props(clip)).waitForDeath()
			log.info "Finished !!"
			sys1.stop()
			sys2.stop()

		then:
			success
	}
}
