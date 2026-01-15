package gimhub.items.containers;

import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

public class ItemContainerInterface {
    /*
     * MenuAction itemOp ID for actions.
     * null indicates the option does not exist, such as emptying the fishing barrel.
     * TODO: check how emptying into the bank works
     *
     * Note: are these actually guaranteed stable, like itemIDs?
     * It seems that default OSRS ones are assigned unique values, while plugin-added ones get -1.
     * I haven't researched thoroughly if plugins or client configuration can change this.
     */

    public final Set<Integer> itemIDs;
    public final Integer viewItemOp;
    public final Integer fillItemOp;
    public final Integer emptyItemOp;

    public ItemContainerInterface(
            Collection<Integer> itemIDs,
            @Nullable Integer viewItemOp,
            @Nullable Integer fillItemOp,
            @Nullable Integer emptyItemOp) {
        this.itemIDs = Set.copyOf(itemIDs);
        this.viewItemOp = viewItemOp;
        this.fillItemOp = fillItemOp;
        this.emptyItemOp = emptyItemOp;
    }
}
