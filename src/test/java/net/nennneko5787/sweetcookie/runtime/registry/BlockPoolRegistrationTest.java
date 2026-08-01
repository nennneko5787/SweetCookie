package net.nennneko5787.sweetcookie.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.nennneko5787.sweetcookie.core.registry.BlockSlot;
import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Actually registers a pool, in a JVM with no client and no window. SC-120 §6.
 *
 * <p><b>Why this exists.</b> Two consecutive real client launches crashed inside
 * {@link BlockPool#register}, and neither could have been caught by anything the project had:
 * {@code chiseledCompile} proves the calls compile, {@code specAll} proves the ledger agrees with
 * itself, and the {@code core} tests never touch Minecraft at all. The first thing that ran
 * {@code Block.<init>} was a client. Both bugs — a missing {@code Properties.setId}, then two
 * {@code IntegerProperty} instances where the identity-keyed state map needs one — are the kind that
 * only appear when the object is really built.
 *
 * <p>{@code Bootstrap.bootStrap()} is what makes that possible without a window: it initialises the
 * registries and nothing graphical. This is the cheapest test in the project that could have failed
 * for a real reason, and it runs on the node's own Minecraft jar, so it checks the version it is
 * compiled against rather than a remembered API.
 */
class BlockPoolRegistrationTest {

    private static BlockPool pool;

    /**
     * Bootstrap and registration in ONE method, because JUnit does not order two @BeforeAll methods
     * against each other. Split across two, registration ran first and died in SoundType's class
     * initialiser - a failure about test wiring wearing the costume of a failure about the mod.
     */
    @BeforeAll
    static void bootstrapAndRegister() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        reopenBlockRegistry();
        // Small on purpose: the mechanism is under test, not the 2,012 blocks the config asks for.
        // Once for the whole class, because there is one block registry and registering per test
        // would put the same identifier in it twice.
        pool = BlockPool.register(new SlotPool(Map.of(1, 2, 4, 3, 16, 2)));
    }

    /**
     * Puts the block registry back into the state mod initialisation actually sees.
     *
     * <p>{@code Bootstrap.bootStrap()} finishes by freezing the built-in registries, and a frozen
     * one refuses to mint the intrusive holder {@code Block}'s constructor asks for. A real client
     * runs mod init <b>before</b> that freeze, so a test that skipped this would be testing a
     * situation the mod is never in — it would fail on correct code and prove nothing.
     *
     * <p>Reflection because there is no {@code unfreeze}. Both fields are private, spelled
     * identically on both supported versions, and this is test-only: nothing in the mod reopens a
     * registry, and SC-120's whole design exists so that nothing ever has to.
     */
    private static void reopenBlockRegistry() throws ReflectiveOperationException {
        Object registry = BuiltInRegistries.BLOCK;
        Class<?> mapped = Class.forName("net.minecraft.core.MappedRegistry");

        Field frozen = mapped.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(registry, false);

        Field intrusive = mapped.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(registry, new IdentityHashMap<>());
    }

    @Test
    void everySlotRegistersAndIsReachable() {
        assertEquals(7, pool.size());
        for (BlockSlot slot : pool.slots()) {
            assertTrue(pool.block(slot).isPresent(), slot + " was not registered");
            assertTrue(BuiltInRegistries.BLOCK.containsKey(BlockPool.identifierOf(slot)),
                    slot + " is not in the block registry");
        }
    }

    @Test
    void aBlockGetsAStateForEveryIndexItsClassPromises() {
        // The second crash in one sentence: the property the state definition was built from has to
        // be the same object the default state is set with, because StateHolder keys by reference.
        PoolBlock block = pool.block(new BlockSlot(16, 0)).orElseThrow();
        assertEquals(16, block.getStateDefinition().getPossibleStates().size());

        for (int index = 0; index < 16; index++) {
            BlockState state = block.defaultBlockState().setValue(block.indexProperty(), index);
            assertEquals(index, state.getValue(block.indexProperty()));
        }
    }

    @Test
    void aSizeOneClassCarriesNoPropertyAndStillHasOneState() {
        PoolBlock block = pool.block(new BlockSlot(1, 0)).orElseThrow();
        assertEquals(1, block.getStateDefinition().getPossibleStates().size());
        assertNull(block.indexProperty());
    }

    @Test
    void blocksOfTheSameClassShareOneProperty() {
        // Not an optimisation - see PoolBlock. Two instances would be equal and useless.
        PoolBlock first = pool.block(new BlockSlot(4, 0)).orElseThrow();
        PoolBlock second = pool.block(new BlockSlot(4, 1)).orElseThrow();
        assertSame(first.indexProperty(), second.indexProperty());
    }

    @Test
    void blocksOfDifferentClassesDoNotShareOne() {
        assertNotSame(pool.block(new BlockSlot(4, 0)).orElseThrow().indexProperty(),
                pool.block(new BlockSlot(16, 0)).orElseThrow().indexProperty());
    }

    @Test
    void aBlockAsksForItsLightBeforeItHasASlotAndSurvives() {
        // Regression. lightLevel is a function and Minecraft calls it while building the state
        // definition, which happens inside Block constructor - so the first question every pool
        // block is ever asked arrives with no slot assigned. Throwing there took the client down on
        // the first of 2,012 blocks. Reaching this line at all is the assertion; the value is the
        // dark default an unbound slot should have.
        PoolBlock block = pool.block(new BlockSlot(4, 0)).orElseThrow();
        assertEquals(0, block.defaultBlockState().getLightEmission());
    }

    @Test
    void aRegisteredBlockHasTheIdItWasRegisteredUnder() {
        // The first crash: Properties carries the ResourceKey now and Block's constructor reads it.
        BlockSlot slot = new BlockSlot(4, 0);
        PoolBlock block = pool.block(slot).orElseThrow();
        assertNotNull(BuiltInRegistries.BLOCK.getKey(block));
        assertEquals(BlockPool.identifierOf(slot), BuiltInRegistries.BLOCK.getKey(block));
    }
}
