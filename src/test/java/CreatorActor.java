import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.actors.lib.NullActor;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.println;

/**
 * Create a fixed number of null Actors as children then kill myself - used in Test
 */
@Slf4j
public class CreatorActor extends Actor {

    int limit;

    /**
     *
     * @param i - number of NullActors to create
     * @return
     */
    public static Props props(Integer i) {
        return Props.create(CreatorActor.class, i);
    }

    public CreatorActor(Integer i) {
        limit = i;
    }

    /**
     * Impolite way to create a lot of Actors
     */
    @Override
    public void preStart()
    {
         while(--limit > 0) {
             try {
                 actorOf(NullActor.props());
             } catch (ActorInstantiationException e) {
                 throw new RuntimeException(e);
             }
         }
         stopSelf();
    }
}
