package gimhub.items.containers;

import javax.annotation.Nullable;

public class ItemContainerInterface {
    /*
     * MenuAction itemOp ID for actions.
     * null indicates the option does not exist, such as emptying the fishing barrel.
     *
     * Note: are these actually guaranteed stable, like itemIDs?
     * It seems that default OSRS ones are assigned unique values, while plugin-added ones get -1.
     * I haven't researched thoroughly if plugins or client configuration can change this.
     */

    public final Integer itemID;
    public final Integer viewItemOp;
    public final Integer fillItemOp;
    public final Integer emptyItemOp;
    // public final Set<Integer> storableItemIDs;

    public ItemContainerInterface(
            Integer itemID,
            //   Set<Integer> storableItemIDs,
            @Nullable Integer viewItemOp,
            @Nullable Integer fillItemOp,
            @Nullable Integer emptyItemOp) {
        this.itemID = itemID;
        // this.storableItemIDs = storableItemIDs;
        this.viewItemOp = viewItemOp;
        this.fillItemOp = fillItemOp;
        this.emptyItemOp = emptyItemOp;
    }
}
