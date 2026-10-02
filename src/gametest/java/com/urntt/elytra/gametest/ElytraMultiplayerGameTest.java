package com.urntt.elytra.gametest;

import static com.urntt.elytra.gametest.GameTestSupport.LOGGER;
import static com.urntt.elytra.gametest.GameTestSupport.bindKey;
import static com.urntt.elytra.gametest.GameTestSupport.check;
import static com.urntt.elytra.gametest.GameTestSupport.configure;
import static com.urntt.elytra.gametest.GameTestSupport.isEnabled;
import static com.urntt.elytra.gametest.GameTestSupport.onlyEnable;
import static com.urntt.elytra.gametest.GameTestSupport.unbindKey;

import com.urntt.elytra.ElytraClient;
import com.urntt.elytra.Feature;
import com.urntt.elytra.Scene;
import com.urntt.elytra.config.MultiplayerMode;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.KeyMapping;

/**
 * Checks the multiplayer modes on a local dedicated server, which the client reaches as {@code localhost}. Instant
 * Fly serves as the probe: a single jump glides only where the mod is active.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElytraMultiplayerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		onlyEnable(context, Feature.INSTANT_FLY);
		KeyMapping toggleKey = bindKey(context, Feature.INSTANT_FLY.toggleKeyName(), "key.keyboard.j");

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			FlightLab lab = new FlightLab(context, server);
			Scene scene = context.computeOnClient(client -> ElytraClient.features().scene());
			LOGGER.info("Connected to {}", scene);
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			checkInstantFly(context, lab, false, "on a server with multiplayer disabled");
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context, Feature.INSTANT_FLY), "a blocked toggle should leave the state unchanged");
			context.takeScreenshot("elytra-blocked-on-server");

			configure(context, config -> {
				config.setServers(List.of("localhost"));
				config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			});
			checkInstantFly(context, lab, true, "on a whitelisted server");

			configure(context, config -> config.setMultiplayerMode(MultiplayerMode.BLACKLIST));
			checkInstantFly(context, lab, false, "on a blacklisted server");
		}

		onlyEnable(context, Feature.ELYTRA_BOOST, Feature.INSTA_STOP);
		unbindKey(context, toggleKey);
	}

	private static void checkInstantFly(final ClientGameTestContext context, final FlightLab lab, final boolean expected,
			final String situation) {
		lab.resetPlayer(0, 0);
		lab.wear("minecraft:elytra");
		lab.pressJump();
		int ticks = lab.ticksUntil(5, client -> client.player.isFallFlying());
		LOGGER.info("Instant Fly {}: glided after {} ticks", situation, ticks);
		check((ticks >= 0) == expected, "Instant Fly should " + (expected ? "" : "not ") + "glide " + situation);
		if (expected) {
			lab.checkServerGliding(true, "the server should accept the glide " + situation);
		}
	}
}
