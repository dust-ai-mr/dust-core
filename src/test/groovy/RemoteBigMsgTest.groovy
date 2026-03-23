import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Mexican standoff. Three Actors - each 'shoots' the other two with a stepwise series of numbered bullets.
 * Success if all the bullets arrive in order for each pair of combatants
 */

class RemoteBigMsgTest extends Specification {


	static ActorSystem sys1 = new ActorSystemBuilder().name("sys1").port(9096).build()
	static ActorSystem sys2 = new ActorSystemBuilder().name("sys2").port(9097).build()


	@Slf4j
	static class Runner extends Actor {
		int deaths = 0, maxSize

		static Props props(int maxSize) {
			Props.create(Runner, maxSize)
		}

		Runner(int maxSize) {
			this.maxSize = maxSize
		}
		void preStart() {
			sys1.context.actorOf( Shooter.props(maxSize), "shooter")
			sys2.context.actorOf( Catcher.props(), "catcher")

			ActorRef shooterRef = watch(sys1.context.actorSelection("dust://localhost:9096/sys1/user/shooter"))
			ActorRef catcherRef = watch(sys2.context.actorSelection("dust://localhost:9097/sys2/user/catcher"))

			shooterRef.tell(new StartMsg(), catcherRef)

		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case Terminated:
						if (++deaths == 2) {
							stopSelf()
						}
						break

					default: log.error "????"

				}
			}
		}
	}

	@Slf4j
	static class Shooter extends Actor {
		int maxSize
		String string = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
		StringBuffer sb = new StringBuffer(string)

		static Props props(int maxSize) {
			Props.create(Shooter, maxSize)
		}

		Shooter(int maxSize) {
			this.maxSize = maxSize
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case StartMsg:
						log.info("Msg size: ${sb.size()}")
						boolean success
						success = sender.tell(sb.toString(), self)
						sb.append(sb.toString())
						if (sb.size() > maxSize) {
							sender.tell(new StopMsg(), null)
							stopSelf()
						}
						if (! success) {
							log.error "Error sending msg length ${sb.size()}"
							sender.tell(new StopMsg(), null)
							stopSelf()
						}
						break

					default:
						log.error "???"
				}
			}
		}
	}

	@Slf4j
	static class Catcher extends Actor {
		LinkedHashMap x;
		Map<ActorRef, Integer> incoming = [:]  //
		Map<ActorRef, Integer> outgoing = [:]

		static Props props() {
			Props.create(Catcher)
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case String:
						sender.tell(new StartMsg(), self)
						break

					case StopMsg:
						stopSelf()
						break

					default:
						log.error "???"
				}
			}
		}



		static class Shot implements Serializable {
			int count

			Shot(int count) {
				this.count = count
			}
		}
	}

	def "Shootout"() {
		when:
			ActorSystem me = new ActorSystemBuilder()
				.name("me")
				.port(9098)
				.build()

			// This should succeed because this limit is < MAX_FRAME_SIZE
			log.info("This should succeed")
			me.context.actorOf(Runner.props(8_000_000)).waitForDeath()
			log.info("This should fail")
			me.context.actorOf(Runner.props(16_000_000)).waitForDeath()
			sys1.stop()
			sys2.stop()
			me.stop()
		then:
			true
	}
}
