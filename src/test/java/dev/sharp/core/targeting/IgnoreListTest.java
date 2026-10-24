package dev.sharp.core.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.config.SharpConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class IgnoreListTest {

    @Test
    void anExactIdMatchesOnlyItself() {
        IgnoreList list = IgnoreList.of(List.of("minecraft:villager"));
        assertTrue(list.ignores("minecraft:villager"));
        assertFalse(list.ignores("minecraft:zombie_villager"));
        assertFalse(list.ignores("minecraft:villager_x"));
        assertFalse(list.ignores("other:villager"));
    }

    @Test
    void aMissingNamespaceMeansMinecraftAndCaseDoesNotMatter() {
        IgnoreList list = IgnoreList.of(List.of("  Villager "));
        assertTrue(list.ignores("minecraft:villager"));
        assertTrue(list.ignores("MINECRAFT:VILLAGER"));
        assertTrue(list.ignores("villager"));
    }

    @Test
    void aStarMatchesAnyRun() {
        IgnoreList list = IgnoreList.of(List.of("minecraft:*boat", "minecraft:*_display", "mymod:*"));
        assertTrue(list.ignores("minecraft:oak_boat"));
        assertTrue(list.ignores("minecraft:oak_chest_boat"));
        assertTrue(list.ignores("minecraft:boat"));
        assertTrue(list.ignores("minecraft:text_display"));
        assertTrue(list.ignores("mymod:anything_at_all"));
        assertFalse(list.ignores("minecraft:displayed"));
        assertFalse(list.ignores("minecraft:boatman"));
    }

    @Test
    void regexCharactersInAnEntryAreLiteral() {
        IgnoreList list = IgnoreList.of(List.of("minecraft:a.b", "minecraft:(x)"));
        assertTrue(list.ignores("minecraft:a.b"));
        assertFalse(list.ignores("minecraft:axb"));
        assertTrue(list.ignores("minecraft:(x)"));
    }

    @Test
    void blankEntriesAndAnEmptyListIgnoreNothing() {
        assertFalse(IgnoreList.of(List.of("", "   ")).ignores("minecraft:sheep"));
        assertFalse(IgnoreList.NONE.ignores("minecraft:sheep"));
    }

    @Test
    void theDefaultListCoversWhatShouldNeverBeClicked() {
        IgnoreList list = new SharpConfig().ignoreList();
        for (String id : List.of(
                "minecraft:end_crystal", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting",
                "minecraft:leash_knot", "minecraft:tnt", "minecraft:oak_boat", "minecraft:bamboo_raft",
                "minecraft:chest_minecart", "minecraft:tnt_minecart", "minecraft:item", "minecraft:experience_orb",
                "minecraft:text_display", "minecraft:block_display", "minecraft:splash_potion", "minecraft:lingering_potion",
                "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:ender_pearl", "minecraft:fireball",
                "minecraft:small_fireball", "minecraft:wind_charge", "minecraft:breeze_wind_charge",
                "minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem", "minecraft:snow_golem",
                "minecraft:allay")) {
            assertTrue(list.ignores(id), id + " should be ignored by default");
        }
    }

    @Test
    void theDefaultListLeavesTheRealTargetsAlone() {
        IgnoreList list = new SharpConfig().ignoreList();
        for (String id : List.of(
                "minecraft:player", "minecraft:mannequin", "minecraft:armor_stand", "minecraft:sheep", "minecraft:cow",
                "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:wolf", "minecraft:horse",
                "minecraft:ender_dragon", "minecraft:zombie_villager")) {
            assertFalse(list.ignores(id), id + " must stay targetable");
        }
    }

    @Test
    void normaliseGivesTheCanonicalSpelling() {
        assertEquals("minecraft:villager", IgnoreList.normalise(" Villager"));
        assertEquals("a:b", IgnoreList.normalise("A:B"));
        assertEquals("", IgnoreList.normalise("  "));
        assertEquals("", IgnoreList.normalise(null));
    }
}
