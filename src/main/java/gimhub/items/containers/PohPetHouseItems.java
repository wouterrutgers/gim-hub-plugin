package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

public class PohPetHouseItems implements TrackedItemContainer {
    private static final int PETS_ENUM = 985;

    private ItemsUnordered ordinaryPets = new ItemsUnordered();
    private ItemsUnordered items;
    private boolean pending;

    @Override
    public String key() {
        return "poh_pet_house";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.POH_MENAGERIE_PETS) {
            Map<Integer, Integer> contents = new HashMap<>();
            for (Item pet : container.getItems()) {
                if (pet.getId() != -1) {
                    contents.merge(pet.getId(), 1, Integer::sum);
                }
            }
            ordinaryPets = new ItemsUnordered(contents, itemManager);
            pending = true;
        }
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varpId == VarPlayerID.PRAYER20
                || varpId == VarPlayerID.MENAGERIE_CONTENTS2
                || varpId == VarPlayerID.MENAGERIE_CONTENTS3) {
            pending = true;
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (!pending && client.getWidget(InterfaceID.PohMenagerie.UNIVERSE) == null) {
            return;
        }

        ItemContainer container = client.getItemContainer(InventoryID.POH_MENAGERIE_PETS);
        if (container != null) {
            onItemContainerChanged(container, itemManager);
        }

        Map<Integer, Integer> contents = new HashMap<>(ordinaryPets.getItemsQuantityByID());
        EnumComposition pets = client.getEnum(PETS_ENUM);
        int firstPets = client.getVarpValue(VarPlayerID.PRAYER20);
        int secondPets = client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS2);
        int thirdPets = client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS3);

        // The second and third varps reserve their top bit for the overview flag.
        for (int index : pets.getKeys()) {
            boolean stored = index < 32
                    ? (firstPets & (1 << index)) != 0
                    : index < 63
                            ? (secondPets & (1 << (index - 32))) != 0
                            : index < 94 && (thirdPets & (1 << (index - 63))) != 0;
            if (stored) {
                contents.merge(pets.getIntValue(index), 1, Integer::sum);
            }
        }

        items = new ItemsUnordered(contents, itemManager);
        pending = false;
    }
}
