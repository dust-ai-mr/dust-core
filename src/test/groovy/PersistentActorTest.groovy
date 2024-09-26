import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.PersistentActor
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.SnapshotMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.services.FSTPersistenceService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import spock.lang.Specification


/**
 * Runs twice. The first time it will reports nulls for postRecovery state. The second time it should show
 * [foo: 'bar'] for postRecovery state. Since it will also delete the snapshots repeating this test should
 * result in the same  2 step results.
 */
@Slf4j
@CompileStatic
class PersistentActorTest extends Specification {

	@Slf4j
	static class TestActor extends PersistentActor {

		HashMap state = [:]

		static Props props() {
			Props.create(TestActor)
		}

		TestActor() {}

		@Override
		protected void postRecovery() {
			log.info "postRecovery state=$state"
			if (null == state) {
				state = [foo: 'bar', path: self.path]
				saveSnapshot(state)
			} else
				deleteSnapshot()
			stopSelf()
		}

		@Override
		protected ActorBehavior recoveryBehavior() {
			(message) -> {
				switch(message) {
					case SnapshotMsg -> {
						state = (HashMap) ((SnapshotMsg)message).snapshot
					}
				}
				// After getting any message switch to my standard behavior
				become(createBehavior())
			}
		}
	}

	def "Persistent Actor Test"() {
		when:
			ActorSystem system = new ActorSystem("Test")
			/*
			  Set the persistence service we are going to use system-wide. Use FSTPersistenceService
			  to serialize to files which will be saved to /tmp
			 */
			system.setPersistenceService( FSTPersistenceService.create('/tmp'))

			(1..20).each {
				system.context.actorOf(TestActor.props(), "$it")
				Thread.sleep(5)
			}
			(1..20).each {
				system.context.actorOf(TestActor.props(), "$it")
				Thread.sleep(5)
			}
			system.stop()
					then:
			true
	}
}
