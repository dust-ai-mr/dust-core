import com.mentalresonance.dust.core.actors.ActorContext
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.ActorSystemBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Create lots of Null Actors. We do by creating an Actor whose job it is to create
 * n batches of m Null actors in each batch. At the end of each batch creation the Actors
 * created in that batch are all destroyed. Thus in the final test below we create
 * 5000000 Actors all of which exist simultaneously (for a shot while).
 */
@Slf4j
@CompileStatic
class Create extends Specification {

	static ActorSystem system
	static ActorContext context
	static success = false
	static int loops = 1000, size = 1000
	static long deltaT1, deltaT2

	def "Multiple Creation"() {
		when:
			system = new ActorSystemBuilder().name("Create").build()
			context = system.context

			log.info "Starting and destroying $loops * $size Actors"
			system.context.actorOf(CreatorMonitorActor.props(loops, size)).waitForDeath()
			deltaT1 = CreatorMonitorActor.tCreated - CreatorMonitorActor.tStart
			deltaT2 = CreatorMonitorActor.tStopped - CreatorMonitorActor.tCreated

			log.info "${loops*size} done as $loops batches of $size"
			log.info "Creation time was ${deltaT1} ms  [${(loops*size*1000f)/deltaT1}] Actors/sec"
			log.info "Destruction time was ~ ${deltaT2} ms  [${(loops*size*1000f)/deltaT2} Actors/Sec]"
			success = system.stop()
		then:
			success
	}

}
