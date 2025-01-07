import com.mentalresonance.dust.core.actors.ActorContext
import com.mentalresonance.dust.core.actors.ActorSystem
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

	/*
	   Create (and test the fact we have) an ActorSystem.
	 */
	def "Can create ActorSystem"() {
		when:
			system = new ActorSystem("Create")
			context = system.context
		then:
			null != context
	}

	def "Multiple Creation"() {
		given:
			def time = System.currentTimeMillis()
		when:
			time = System.currentTimeMillis()
			// Now create 5 million actors in 500 batches
			log.info "Starting 1000 * 5000"
			system.context.actorOf(CreatorMonitorActor.props(1000, 5000)).waitForDeath()
			time = System.currentTimeMillis() - time
			log.info "5,000,000 done as 500 batches of 10000.  ${ 5000000 / (time / 1000.0)} actors created / second"
			system.stop()
			success = true
		then:
			success
	}

}
