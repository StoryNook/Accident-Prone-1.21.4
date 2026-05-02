package com.storynook.PlayerStatsManagement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.storynook.CommandHandler;
import com.storynook.PlaySounds;
import com.storynook.Plugin;
import com.storynook.AccidentsANDWanrings.Warnings;

public class UpdateStats {
    private final CommandHandler commandHandler;

    public static Map<UUID, Integer> playerCyclesMap = new HashMap<>();
    public static Map<UUID, Integer> playerSecondsLeftMap = new HashMap<>();
    public static Map<UUID, Boolean> playerWarningsMap = new HashMap<>();
    public static Map<UUID, Boolean> playerWarningsType = new HashMap<>();
    public static Map<UUID, Integer> Startingdelay = new HashMap<>();
    // public static Map<UUID, Boolean> HitZero = new HashMap<>();

    public static HashMap<UUID, Double> bladderfill = new HashMap<>();
    public static HashMap<UUID, Double> bowelfill = new HashMap<>();
    public static HashMap<UUID, Double> activityMultiplier = new HashMap<>();
    public static HashMap<UUID, Integer> HydrationSpike = new HashMap<>();
    static HashMap<UUID, Boolean> Growled = new HashMap<>();
    static HashMap<UUID, Boolean> Dehydrated = new HashMap<>();

    private final Plugin plugin;
        public UpdateStats(Plugin plugin, CommandHandler commandHandler) {
            this.plugin = plugin;
            this.commandHandler = commandHandler;
        } 
        // Near Running Water
        public static boolean isNearRunningWater(Player player) {
            Location playerLocation = player.getLocation();
            World world = player.getWorld();
    
            // Define the search radius around the player
            int radius = 5;
    
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) { // Check one block above and below the player
                    for (int z = -radius; z <= radius; z++) {
                        // Get the block at the current offset
                        Block block = world.getBlockAt(playerLocation.clone().add(x, y, z));
    
                        // Check if the block is water and is flowing
                        if (block.getType() == Material.WATER) {
                            BlockData data = block.getBlockData();
                            if (data instanceof Levelled) {
                                Levelled water = (Levelled) data;
    
                                // Flowing water has a level > 0
                                if (water.getLevel() > 0) {
                                    return true; // Found flowing water nearby
                                }
                            }
                        }
                    }
                }
            }
    
            return false; // No running water found nearby
        }
    
        public static boolean isOutsideInRain(Player player) {
            World world = player.getWorld();
    
            // Check if it is raining in the world
            if (!world.hasStorm()) {
                return false; // No rain, player cannot be in the rain
            }
    
            Location playerLocation = player.getLocation();
            int playerX = playerLocation.getBlockX();
            int playerY = playerLocation.getBlockY();
            int playerZ = playerLocation.getBlockZ();
    
            // Check for blocks directly above the player
            for (int y = playerY + 1; y <= world.getMaxHeight(); y++) {
                Block block = world.getBlockAt(playerX, y, playerZ);
    
                if (!block.isPassable()) { // A non-passable block is blocking the rain
                    return false; // Player is sheltered from the rain
                }
            }
    
            return true; // No blocks above, and it's raining
        }
    
        public void Update(){
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
            if (commandHandler.NightVisionToggle.getOrDefault(player.getUniqueId(), false))
            {
              player.removePotionEffect(PotionEffectType.NIGHT_VISION);
              player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 1), true);
            }

            if (stats != null && stats.getOptin() 
            && !(player.getVehicle() instanceof ArmorStand 
                && ((ArmorStand) player.getVehicle()).getCustomName().equals("ToiletArmor"))) { 
              
              //Set the bladder and bowel rate to the config file
              double configBladderRate = plugin.getConfig().getDouble("Bladder_Fill_Rate", 0.2);
              double configBowelRate = plugin.getConfig().getDouble("Bowel_Fill_Rate", 0.14);

              double hydrationDecreaseRate = (0.1 * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0));
              //Nether Dehydration multiplier
              if (player.getLocation().getWorld().getEnvironment() == World.Environment.NETHER) {hydrationDecreaseRate = hydrationDecreaseRate * 2;}
              //Lax wore off reset
              if (stats.getLaxEffectDuration() == 0) {
                 stats.setBowelFillRate(configBowelRate);
                 stats.setLaxEffectIntensity(0);
              }
              //Duiretic wore off reset
              if (stats.getDurEffectDuration() == 0) {
                 stats.setBladderFillRate(configBladderRate);
              }

              double BladderAdjustedRate = 0;
              if (stats.getHydration() > 100) {
                BladderAdjustedRate = ((stats.getHydration()-100)/100);
                hydrationDecreaseRate += ((stats.getHydration()-100)/100);
              }
              //Decrease Hydration after Hydration check, and activitity multiplier
              stats.decreaseHydration(hydrationDecreaseRate);
              boolean isUnderwaterBreathing = player.getEyeLocation().getBlock().getType() == Material.WATER
                && (player.hasPotionEffect(PotionEffectType.WATER_BREATHING)
                    || player.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
              if (isUnderwaterBreathing) {
                stats.increaseHydration(0.3);
                BladderAdjustedRate += (stats.getBladderFillRate() * 3) * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0);
                if(HydrationSpike.getOrDefault(player.getUniqueId(), 0) > 0){HydrationSpike.put(player.getUniqueId(), (HydrationSpike.get(player.getUniqueId()) - 1));}
              } else if (HydrationSpike.getOrDefault(player.getUniqueId(), 0) > 0 || isNearRunningWater(player) || isOutsideInRain(player)) {
                BladderAdjustedRate += (stats.getBladderFillRate() * 2) * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0);
                if(HydrationSpike.getOrDefault(player.getUniqueId(), 0) > 0){HydrationSpike.put(player.getUniqueId(), (HydrationSpike.get(player.getUniqueId()) - 1));}
              } else {BladderAdjustedRate += stats.getBladderFillRate() * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0);}
              if (stats.getHydration() < 30) {
                BladderAdjustedRate = 0.1 * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0);
              }
              stats.increaseBladder(BladderAdjustedRate);
              bladderfill.put(player.getUniqueId(), Math.round((BladderAdjustedRate * 100)) / 100.0);
              //If the Bowel fillrate is higher than the base, reduce lax effect
              if (stats.getBowelFillRate() > configBowelRate) {
                stats.decreaseLaxEffectDuration(1);
              }
              if (stats.getLaxEffectDelay() > 0) {
                stats.setLaxEffectDelay(stats.getLaxEffectDelay() - 1);
              }
              if (stats.getLaxEffectDelay() == 0 && stats.getLaxEffectDuration() > 0) {
                Growled.remove(player.getUniqueId());
                Startingdelay.remove(player.getUniqueId());
                if (stats.getBowelFillRate() ==  configBowelRate) {
                  int intensity = Math.max(1, stats.getLaxEffectIntensity());
                  double base = (Math.random() * 8) + 5;
                  double severity = base * (1 + (intensity - 1) * 0.4);
                  stats.setBowelFillRate(stats.getBowelFillRate() * severity);
                }
              }
              if (stats.getLaxEffectDelay() > 0 && !Growled.getOrDefault(player.getUniqueId(), false)) {
                int startingDelay = Startingdelay.getOrDefault(player.getUniqueId(), 0);
                  // Calculate a randomized threshold between 0 and half of the starting delay
                  double randomThreshold = (startingDelay / 2.0) * (1 - Math.random() * 0.5);
                  if (stats.getLaxEffectDelay() < randomThreshold) {
                      Growled.put(player.getUniqueId(), true);
                      PlaySounds.playsounds(player, "tummyrumble", 5, 1.0, 0.2, false);
                      if(!stats.getHardcore()){player.sendMessage(plugin.getRandomMessage("Laxative"));}
                  }
              }

              if (stats.getMessing()) {
                double saturation = player.getSaturation();
                int hunger = player.getFoodLevel();

                double adjustedRate;

                if (saturation > 0) {
                    // While saturation > 0, base fill rate on saturation depletion
                    double saturationImpact = Math.min(saturation / 20.0, 1.0); // Scales from 0 to 1
                    adjustedRate = stats.getBowelFillRate() * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0) * (1 + saturationImpact);
                } else {
                    double hungerImpact = Math.min(2.0, Math.max(1.0, 1.5 - (hunger / 40.0)));
                    adjustedRate  = stats.getBowelFillRate() * activityMultiplier.getOrDefault(player.getUniqueId(), 1.0) * hungerImpact;
                }

                stats.increaseBowels(adjustedRate);
                bowelfill.put(player.getUniqueId(), Math.round((adjustedRate * 100)) / 100.0);
              }
              
              // Capture RP before accumulation so the effects block can detect threshold crossings
              float prevRP = stats.getRashPoints();
              {
                double rpTick = -5.0;
                double rpFullness = stats.getDiaperFullness();
                double rpWetness = stats.getDiaperWetness();
                if (rpFullness >= 100) rpTick += 40;
                else if (rpFullness >= 50) rpTick += 25;
                else if (rpFullness >= 20) rpTick += 10;
                if (rpWetness >= 95) rpTick += 20;
                else if (rpWetness >= 80) rpTick += 8;
                else if (rpWetness >= 40) rpTick = Math.max(rpTick, 0);
                // Plain underwear (type 1) accelerates recovery when clean and dry
                if (stats.getUnderwearType() == 1 && rpFullness < 20 && rpWetness < 40) rpTick -= 20;
                stats.setRashPoints((float) Math.max(0, prevRP + rpTick));
              }
              if (stats.getDiaperFullness() > 0) {
                
                int underweartype = stats.getUnderwearType();
                int diaperFullness = (int) stats.getDiaperFullness();
                
                // Define thresholds for each underwear type
                int[][] fullnessThresholds = {
                    {100},                     // Level 0: 1 threshold at 100%
                    {50, 100},                 // Level 1: thresholds at 50% and 100%
                    {33, 66, 100},             // Level 2: thresholds at ~33%, ~66%, and 100%
                    {25, 50, 75, 100}          // Level 3: thresholds at 25%, 50%, 75%, and 100%
                };
                
                int slownessLevel = 0;
                int[] thresholdsForType = fullnessThresholds[underweartype];
                
                // Iterate from highest to lowest threshold
                for (int i = thresholdsForType.length - 1; i >= 0; i--) {
                    if (diaperFullness >= thresholdsForType[i]) {
                        slownessLevel = i + 1;
                        break;
                    }
                }
                
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                if (slownessLevel > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, slownessLevel - 1), true);
                }
              }
              else if(stats.getDiaperFullness() > 0 && stats.getUnderwearType() < 1){
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1), true);
              }
              else if (stats.getDiaperFullness() == 0) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
              }
              {
                Map<String, Object> gc = plugin.getGlobalConfig();
                double rashThreshold = ((Number) gc.getOrDefault("Rash_Threshold", 250.0)).doubleValue();
                String rashMode = (String) gc.getOrDefault("Rash_Mode", "poison");
                double rashAmount = ((Number) gc.getOrDefault("Rash_Amount", 1.0)).doubleValue();
                boolean rashSlowness = (Boolean) gc.getOrDefault("Rash_Slowness", Boolean.TRUE);
                float newRP = stats.getRashPoints();
                boolean wasAbove = prevRP >= rashThreshold;
                boolean isAbove = newRP >= rashThreshold;

                if (!wasAbove && isAbove) {
                  player.sendMessage(plugin.getRandomMessage("Rash"));
                } else if (wasAbove && !isAbove) {
                  player.sendMessage(plugin.getRandomMessage("Rash_Clear"));
                }

                if (isAbove) {
                  switch (rashMode) {
                    case "poison":
                      player.removePotionEffect(PotionEffectType.POISON);
                      player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 0), true);
                      break;
                    case "damage":
                      if (player.getHealth() > 1) {
                        double dmg = Math.min(rashAmount, player.getHealth() - 0.5);
                        if (dmg > 0) player.damage(dmg);
                      }
                      break;
                    default:
                      break;
                  }
                  if (rashSlowness && player.getPotionEffect(PotionEffectType.SLOWNESS) == null) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0), true);
                  }
                } else {
                  player.removePotionEffect(PotionEffectType.POISON);
                }

                // health_reduction: reconcile max-health attribute modifier each tick
                if ("health_reduction".equals(rashMode)) {
                  org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                  if (attr != null) {
                    org.bukkit.attribute.AttributeModifier toRemove = null;
                    for (org.bukkit.attribute.AttributeModifier m : attr.getModifiers()) {
                      if ("rash_max_health".equals(m.getName())) { toRemove = m; break; }
                    }
                    if (toRemove != null) attr.removeModifier(toRemove);
                    if (isAbove) {
                      attr.addModifier(new org.bukkit.attribute.AttributeModifier(
                        java.util.UUID.fromString("b3c4d5e6-f7a8-9b0c-1d2e-3f4a5b6c7d8e"),
                        "rash_max_health", -(rashAmount * 2.0),
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
                      double newMax = attr.getValue();
                      if (player.getHealth() > newMax) player.setHealth(newMax);
                    }
                  }
                }
              }
              if (stats.getHydration() < 10) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 50, 2), true);
                Dehydrated.put(player.getUniqueId(), true);
              }
              else if (stats.getHydration() >= 10 && Dehydrated.getOrDefault(player.getUniqueId(), false)) {
                player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                Dehydrated.put(player.getUniqueId(), false);
              }
              int cycles = playerCyclesMap.getOrDefault(player.getUniqueId(), 0);
              int secondsLeft = playerSecondsLeftMap.getOrDefault(player.getUniqueId(), 0);
              boolean warning = playerWarningsMap.getOrDefault(player.getUniqueId(), false);
              if (!warning && cycles > 6) {
                secondsLeft = 0;
                Warnings.triggerWarnings(player, stats);
              }
              else {
                if (cycles > 7 && warning) {
                  cycles = 0;
                }
                if (warning) {
                  secondsLeft++;
                  // boolean isBladder = (stats.getBladder() * stats.getBladderIncontinence()) > (stats.getBowels() * stats.getBowelIncontinence()) ? true : false;
                  double fullness = playerWarningsType.getOrDefault(player.getUniqueId(), null) ? stats.getBladder() : stats.getBowels();
                  double incontinenceLevel = playerWarningsType.getOrDefault(player.getUniqueId(), null) ? stats.getBladderIncontinence() : stats.getBowelIncontinence();
                  Warnings.sneakCheck(player, stats, fullness, incontinenceLevel, playerWarningsType.getOrDefault(player.getUniqueId(), null));
                }
              }
              cycles++;
              playerCyclesMap.put(player.getUniqueId(), cycles);
              playerSecondsLeftMap.put(player.getUniqueId(), secondsLeft);
          }
        }
    }
}
