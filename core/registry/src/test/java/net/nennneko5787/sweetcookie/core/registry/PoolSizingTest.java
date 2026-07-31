package net.nennneko5787.sweetcookie.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** Startup pool sizing. SC-120 §6.2, §8.1, §10. */
@ProvesSpec("SC-120")
class PoolSizingTest {

    @Test
    @ProvesSpec("SC-120")
    void registersTheConfiguredPoolWhenNoWorldNeedsMore() {
        PoolSizing.Result result = PoolSizing.effective(
                PoolSizing.Config.DEFAULT, List.of(new SlotPool(Map.of(16, 4))));

        PoolSizing.Result.Register register =
                assertInstanceOf(PoolSizing.Result.Register.class, result);
        assertFalse(register.grew());
        assertEquals(SlotPool.DEFAULT, register.pool());
    }

    @Test
    @ProvesSpec("SC-120")
    void growsToFitTheLargestWorldAndNeverShrinks() {
        // Element-wise across EVERY world, not the one about to load: the registry freezes before
        // a world is selected, so a size a different save needs cannot be added later at any price.
        PoolSizing.Result.Register register = assertInstanceOf(
                PoolSizing.Result.Register.class,
                PoolSizing.effective(PoolSizing.Config.DEFAULT, List.of(
                        new SlotPool(Map.of(16, 200)),
                        new SlotPool(Map.of(16, 150, 64, 100)))));

        assertTrue(register.grew());
        assertEquals(200, register.pool().capacity(16), "the largest requirement wins");
        assertEquals(100, register.pool().capacity(64));
        assertEquals(1024, register.pool().capacity(1), "untouched classes keep the default");
    }

    @Test
    @ProvesSpec("SC-120")
    void refusesRatherThanEnlargingWhenTheOperatorPinnedTheSize() {
        // blockPoolAutoGrow: false exists for an operator who wants the palette size fixed. Quietly
        // enlarging it would be the one thing they asked not to happen.
        PoolSizing.Result.Refuse refuse = assertInstanceOf(
                PoolSizing.Result.Refuse.class,
                PoolSizing.effective(
                        new PoolSizing.Config(SlotPool.DEFAULT, false),
                        List.of(new SlotPool(Map.of(16, 200)))));

        assertEquals(1, refuse.shortfall().size());
        PoolSizing.Growth growth = refuse.shortfall().get(0);
        assertEquals(16, growth.sizeClass());
        assertEquals(128, growth.configured());
        assertEquals(200, growth.required());
        assertEquals(72, growth.shortfall());
    }

    @Test
    @ProvesSpec("SC-120")
    void spellsOutTheConfigChangeRatherThanFailingGenerically() {
        // SC-120 §8.1: the operator must be told the class, the shortfall and the exact line to
        // change. A generic failure leaves them with no way forward.
        PoolSizing.Growth growth = new PoolSizing.Growth(64, 64, 67);
        assertEquals("sweetcookie.blockPool.64 = 67 (currently 64, 3 more needed)",
                growth.advice());
    }

    @Test
    @ProvesSpec("SC-120")
    void anInstanceWithNoWorldsRegistersTheConfiguredPool() {
        PoolSizing.Result.Register register = assertInstanceOf(
                PoolSizing.Result.Register.class,
                PoolSizing.effective(PoolSizing.Config.DEFAULT, List.of()));
        assertEquals(SlotPool.DEFAULT, register.pool());
        assertFalse(register.grew());
    }

    @Test
    @ProvesSpec("SC-120")
    void reportsGrowthEvenWhenItProceeds() {
        // Growing is normal and still worth logging: the pool costs palette space in every world,
        // so an operator should be able to see when one save enlarged it for all of them.
        PoolSizing.Result.Register register = assertInstanceOf(
                PoolSizing.Result.Register.class,
                PoolSizing.effective(PoolSizing.Config.DEFAULT,
                        List.of(new SlotPool(Map.of(4096, 9)))));
        assertTrue(register.grew());
        assertEquals(1, register.growth().size());
        assertEquals(4096, register.growth().get(0).sizeClass());
    }
}
