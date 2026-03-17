import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.actors.lib.PingActor
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Mexican standoff. Three Actors - each 'shoots' the other two with a stepwise series of numbered bullets.
 * Success if all the bullets arrive in order for each pair of combatants
 */

class RemoteShootOut extends Specification {


	static ActorSystem sys1 = new ActorSystem("sys1", 9096)
	static ActorSystem sys2 = new ActorSystem("sys2", 9097)
	static ActorSystem sys3 = new ActorSystem("sys3", 9098)
	static ActorSystem me = new ActorSystem("sys3", 9099)

	static success = true

	@Slf4j
	static class Runner extends Actor {
		int shots, deaths=0

		static Props props(int shots) {
			Props.create(Runner, shots)
		}

		Runner(int shots) {
			this.shots = shots
		}

		void preStart() {
			sys1.context.actorOf( Shooter.props(shots), "s1")
			sys2.context.actorOf( Shooter.props(shots), "s2")
			sys3.context.actorOf( Shooter.props(shots), "s3")

			ActorRef a1Ref = watch(sys1.context.actorSelection("dust://localhost:9096/sys1/user/s1"))
			ActorRef a2Ref = watch(sys2.context.actorSelection("dust://localhost:9097/sys2/user/s2"))
			ActorRef a3Ref = watch(sys3.context.actorSelection("dust://localhost:9098/sys3/user/s3"))

			a1Ref.tell(a2Ref, null)
			a1Ref.tell(a3Ref, null)
			a2Ref.tell(a1Ref, null)
			a2Ref.tell(a3Ref, null)
			a3Ref.tell(a1Ref, null)
			a3Ref.tell(a2Ref, null)
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case Terminated:
						if (++deaths == 3) {
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
		LinkedHashMap x;
		def shots
		Map<ActorRef, Integer> incoming = [:]  //
		Map<ActorRef, Integer> outgoing = [:]

		static Props props(int shots) {
			Props.create(Shooter, shots)
		}

		Shooter(int shots) {
			this.shots = shots
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case ActorRef:
						ActorRef msg = (ActorRef)message
						msg.context = context
						outgoing[message] = shots
						if (outgoing.size() == 2)
							tellSelf(new StartMsg())
						break

					case Shot:
						Shot shot = (Shot) message
						if (null == incoming[sender])
							incoming[sender] = 0
						if (incoming[sender] == shot.count - 1) {  // Must be in sequence
							incoming[sender] = shot.count
							//log.info("{} Shot by {}: current={}", self.path, sender, incoming[sender])
						} else {
							log.error("{} Out of sequence from {}: current={}, next shot is {}", self.path, sender, incoming[sender], shot.count)
							success = false
							stopSelf()
						}
						break

					case StartMsg:
						outgoing.each {
							if (it.value > 0) {
								it.key.tell(new Shot(1 + shots - it.value), self)
								it.value = it.value - 1
								//log.info "${self.path} shot ${it.key}"
							}
						}
						if (
							incoming.size() == 2 &&
							outgoing.size() == 2 &&
							(!outgoing.values().find {it > 0 }) &&
							(!incoming.values().find { it != shots })
						) {
							log.info "Stopping ${self.path}  ${outgoing.values().toList()}  ${incoming.values().toList()}"
							stopSelf()
						} else {
							tellSelf(message)
						}
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
			def clip = 500000

			long time = System.currentTimeMillis()

			me.context.actorOf(Runner.props(clip)).waitForDeath()

			time = System.currentTimeMillis() - time
			log.info "Finished: ${6*clip} in $time ms (${1000.0f*6*clip / time} msgs / sec)"
			if (success) log.info "Success !!"
			me.stop()
			sys1.stop()
			sys2.stop()
			sys3.stop()
		then:
			success
	}
}
