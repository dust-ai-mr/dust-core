import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.StartMsg
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Mexican standoff. Three Actors - each 'shoots' the other two with a stepwise series of numbered bullets.
 * Success if all the bullets arrive in order for each pair of combatants
 */

class ShootOut extends Specification {

	static ActorSystem me = new ActorSystem("me")
	static success = true

	@Slf4j
	static class Shooter extends Actor {
		def shots
		Map<ActorRef, Integer> incoming = [:]  //
		Map<ActorRef, Integer> outgoing = [:]

		static Props props(int shots) {
			Props.create(Shooter, shots)
		}

		Shooter(int shots) {
			this.shots = shots
		}

		void preStart() {
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case ActorRef:
						outgoing[message] = shots
						tellSelf(new StartMsg())
						break

					case Shot:
						Shot shot = (Shot) message
						if (null == incoming[sender])
							incoming[sender] = 0
						if (incoming[sender] == shot.count - 1) {  // Must be in sequence
							incoming[sender] = shot.count
						} else {
							log.error("Out of sequence")
							success = false
							stopSelf()
						}
						break

					case StartMsg:
						outgoing.each {
							if (it.value > 0) {
								it.key.tell(new Shot(1 + shots - it.value), self)
								it.value = it.value - 1
							}
						}
						//log.info "${self.name} Shots left = $outgoing"
						//log.info "${self.name} hits taken = $incoming"
						// Nothing left to shoot and we are all shot up
						if ( (!outgoing.values().find {it > 0 }) && (!incoming.values().find { it != shots })) {
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
			def clip = 1000000
			ActorRef a1Ref = me.context.actorOf( Shooter.props(clip), "s1")
			ActorRef a2Ref = me.context.actorOf( Shooter.props(clip), "s2")
			ActorRef a3Ref = me.context.actorOf( Shooter.props(clip), "s3")
			long time = System.currentTimeMillis()
			a1Ref.tell(a2Ref, null)
			a1Ref.tell(a3Ref, null)
			a2Ref.tell(a1Ref, null)
			a2Ref.tell(a3Ref, null)
			a3Ref.tell(a1Ref, null)
			a3Ref.tell(a2Ref, null)
			a1Ref.waitForDeath()
			a2Ref.waitForDeath()
			a3Ref.waitForDeath()
			time = System.currentTimeMillis() - time

			log.info "Finished: ${6*clip} in $time ms (${1000.0f*6*clip / time} msgs / sec)"
		then:
			success
	}
}
