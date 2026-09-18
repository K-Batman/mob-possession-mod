package com.kaius.mobpossession;

import com.kaius.mobpossession.possession.PossessionEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mob Possession
 *
 * Right-click a mob (empty hand) to jump into its body and control it.
 * Sneak (shift) while possessing to get back out.
 *
 * Built by Kaius.
 */
public class MobPossessionMod implements ModInitializer {

	public static final String MOD_ID = "mobpossession";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Mob Possession loading up - right-click a mob, sneak to let go.");
		PossessionEvents.register();
	}
}
