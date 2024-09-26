import com.mentalresonance.dust.core.actors.ActorTrait;

/**
 * Adds a simple method via the interface/trait that the implementing Actor will call. Part of tests
 */
public interface TestTrait extends ActorTrait {

    default void callMe() {
        System.out.println(String.format("MyActorRef - %s - MyActor %s", getSelf(), this));
    }
}
