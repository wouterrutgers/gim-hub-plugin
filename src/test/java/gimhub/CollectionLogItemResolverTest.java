package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CollectionLogItemResolverTest {
    @Test
    public void selectsTheLowestUnnotedIdentifierForDuplicateNames() {
        CollectionLogItemResolver resolver = new CollectionLogItemResolver();

        resolver.populate(Map.of(300, "Dragon axe", 100, "Dragon axe", 200, "Dragon axe"), Set.of(100));

        assertEquals(Integer.valueOf(200), resolver.findItemIdentifier("Dragon axe"));
    }

    @Test
    public void excludesNotedItemsAndUsesExactNames() {
        CollectionLogItemResolver resolver = new CollectionLogItemResolver();

        resolver.populate(Map.of(100, "Dragon axe", 101, "Rune axe"), Set.of(101));

        assertEquals(Integer.valueOf(100), resolver.findItemIdentifier("Dragon axe"));
        assertNull(resolver.findItemIdentifier("dragon axe"));
        assertNull(resolver.findItemIdentifier("Rune axe"));
    }
}
