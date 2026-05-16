package com.storynook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.ScoreboardManager;

import com.storynook.AccidentsANDWanrings.HandleAccident;
import com.storynook.AccidentsANDWanrings.Warnings;
import com.storynook.Event_Listeners.*;
import com.storynook.Integrations.VentureChatHook;
import com.storynook.PlayerStatsManagement.*;
import com.storynook.items.*;
import com.storynook.items.Nanny;
import com.storynook.menus.*;
import com.storynook.nanny.NannyManager;
import com.storynook.nanny.NannyChatEngine;
import com.storynook.nanny.BehaviorScoreboard;
import com.storynook.nanny.BehaviorSignals;
import com.storynook.nanny.DisciplineDispatcher;
import com.storynook.Integrations.NannyVentureChatHook;
import com.storynook.Commands.NannyCommand;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Plugin extends JavaPlugin implements com.storynook.Integrations.IIntegrationsBusHost
{
  public HashMap<UUID, PlayerStats> playerStatsMap = new HashMap<>();
  private final Map<UUID, ArmorStand> armorStandTracker = new HashMap<>();
  public HashMap<UUID, Integer> rightclickCount = new HashMap<>();
  HashMap<UUID, Boolean> wasJumping = new HashMap<>();
  HashMap<UUID, Boolean> wasSprinting = new HashMap<>();
  public HashMap<UUID, Boolean> firstimeran = new HashMap<>();
  private Map<UUID, BukkitTask> ParticleEffects = new HashMap<>();
  public static Map<String, Map<String, Boolean>> soundConfig = new HashMap<>();
  private Map<String, Object> globalConfig;
  private Map<String, Object> integrationsConfig;
  private Map<String, String> jobsActionMap = new HashMap<>();
  private Map<String, String> beautyQuestsTriggerMap = new HashMap<>();
  public Map<String, Object> getIntegrationsConfig() { return integrationsConfig; }
  public Map<String, String> getJobsActionMap() { return jobsActionMap; }
  public Map<String, String> getBeautyQuestsTriggerMap() { return beautyQuestsTriggerMap; }
  private com.storynook.Integrations.IntegrationsBus integrationsBus;
  public com.storynook.Integrations.IntegrationsBus getIntegrationsBus() { return integrationsBus; }
  private List<String> hypnoWords;
  private FileConfiguration messagesConfig;
  public boolean VentureChat;
  public boolean PlaceholderAPI = false;
  public boolean citizensEnabled;
  private NannyManager nannyManager;
  private NannyCommand nannyCommand;
  private com.storynook.furniture.CribRegistry cribRegistry;
  public com.storynook.furniture.CribRegistry getCribRegistry() { return cribRegistry; }

  private com.storynook.furniture.highchair.HighchairRegistry highchairRegistry;
  public com.storynook.furniture.highchair.HighchairRegistry getHighchairRegistry() { return highchairRegistry; }

  private com.storynook.furniture.highchair.HighchairListener highchairListener;
  public com.storynook.furniture.highchair.HighchairListener getHighchairListener() { return highchairListener; }

  private com.storynook.furniture.changingtable.ChangingTableRegistry changingTableRegistry;
  public com.storynook.furniture.changingtable.ChangingTableRegistry getChangingTableRegistry() { return changingTableRegistry; }

  private com.storynook.furniture.changingtable.ChangingTableListener changingTableListener;
  public com.storynook.furniture.changingtable.ChangingTableListener getChangingTableListener() { return changingTableListener; }

  private com.storynook.furniture.changingtable.ChangingTablePdcKeys changingTablePdcKeys;
  public com.storynook.furniture.changingtable.ChangingTablePdcKeys getChangingTablePdcKeys() { return changingTablePdcKeys; }

  private com.storynook.furniture.changingtable.ChangingTableInventoryManager changingTableInventoryManager;
  public com.storynook.furniture.changingtable.ChangingTableInventoryManager getChangingTableInventoryManager() {
      return changingTableInventoryManager;
  }

  private com.storynook.furniture.CribPdcKeys cribPdcKeys;
  private com.storynook.furniture.KnockbackTracker knockbackTracker;
  private com.storynook.furniture.carry.CarryManager carryManager;
  public com.storynook.furniture.carry.CarryManager getCarryManager() { return carryManager; }
  public com.storynook.furniture.CribPdcKeys getCribPdcKeysForDebug() { return cribPdcKeys; }
  private com.storynook.nanny.crypto.CryptoService cryptoService;
  private com.storynook.nanny.membership.OAuthHelper oauthHelper;
  private com.storynook.nanny.DiaperPunishment diaperPunishment;
  public com.storynook.nanny.DiaperPunishment getDiaperPunishment() { return diaperPunishment; }

  public com.storynook.nanny.crypto.CryptoService getCryptoService() { return cryptoService; }
  public com.storynook.nanny.membership.OAuthHelper getOAuthHelper() { return oauthHelper; }
  public Map<String, Object> getGlobalConfig() {
    return globalConfig;
  }
  public NannyManager getNannyManager() {
    return nannyManager;
  }
  public NannyCommand getNannyCommand() {
    return nannyCommand;
  }
  private TutorialBook tutorialbook;


  public Map<UUID, ArmorStand> getArmorStandTracker() {
      return armorStandTracker;
  }

  // Session-only state for the warning-driven toilet relief system.
  // Cleared on server restart; survives re-logs during a session by design.
  private final Map<UUID, Long> lastBladderWarningMillis = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastBowelWarningMillis = new ConcurrentHashMap<>();
  private final Set<UUID> playersOnToilet = ConcurrentHashMap.newKeySet();

  public long getLastBladderWarningMillis(UUID id) { return lastBladderWarningMillis.getOrDefault(id, 0L); }
  public long getLastBowelWarningMillis(UUID id)   { return lastBowelWarningMillis.getOrDefault(id, 0L); }
  public void stampBladderWarning(UUID id) { lastBladderWarningMillis.put(id, System.currentTimeMillis()); }
  public void stampBowelWarning(UUID id)   { lastBowelWarningMillis.put(id, System.currentTimeMillis()); }

  public boolean isOnToilet(UUID id) { return playersOnToilet.contains(id); }
  public void markOnToilet(UUID id)  { playersOnToilet.add(id); }
  public void clearOnToilet(UUID id) { playersOnToilet.remove(id); }

  public FileConfiguration getMessagesConfig() {
      return messagesConfig;
  }

  private CommandHandler commandHandler = new CommandHandler(this);
  private HandleAccident handleAccident = new HandleAccident(this);
  private PlaySounds playsounds = new PlaySounds(this);
  private Warnings warnings = new Warnings(this);
  private ScoreboardManager manager;

  @Override
  public void onEnable()
  {
    getLogger().info("Plugin started, onEnable");
    DesignRegistry.init();
    PaciRegistry.init();
    if (getServer().getPluginManager().getPlugin("VentureChat") != null) {
        getLogger().info("VentureChat found; enabling VC integration.");
        getServer().getPluginManager().registerEvents(new VentureChatHook(this), this);
        VentureChat = true;
        VentureChatHook Venture = new VentureChatHook(this);
        getServer().getPluginManager().registerEvents(Venture, this);
    } else {
        getLogger().info("VentureChat not found; using fallback chat detection.");
        VentureChat = false;
        // getServer().getPluginManager().registerEvents(new VanillaChatHook(this), this);
    }
    org.bukkit.plugin.Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
    if (papi != null && papi.isEnabled()) {
        getLogger().info("PlaceholderAPI found; balance display enabled.");
        PlaceholderAPI = true;
    } else {
        getLogger().info("PlaceholderAPI not found; balance display disabled.");
        PlaceholderAPI = false;
    }
    org.bukkit.plugin.Plugin citizensPlugin = getServer().getPluginManager().getPlugin("Citizens");
    if (citizensPlugin != null && citizensPlugin.isEnabled()) {
        getLogger().info("Citizens2 found; enabling Nanny NPC system.");
        citizensEnabled = true;
    } else if (citizensPlugin != null) {
        getLogger().warning("Citizens2 is installed but failed to enable; Nanny NPC system disabled.");
        citizensEnabled = false;
    } else {
        getLogger().info("Citizens2 not found; Nanny NPC system disabled.");
        citizensEnabled = false;
    }
    //Creates DataFoler if it doesn't exist.
    if (!getDataFolder().exists()) {
      getDataFolder().mkdirs();
    }
    File playerDataFolder = new File(getDataFolder(), "players");
    if (!playerDataFolder.exists()) {
      try{
        playerDataFolder.mkdirs();
        getLogger().info("Player data folder created");
      } catch (Exception e) {
        getLogger().warning("FIALED:Player data folder not created");
      }
    }
    File DiaperPailInventoryFolder = new File(getDataFolder(), "DiaperPails");
    if (!DiaperPailInventoryFolder.exists()) {
      try{
        DiaperPailInventoryFolder.mkdirs();
        getLogger().info("DiaperPail Inventory folder created");
      } catch (Exception e) {
        getLogger().warning("FIALED:DiaperPail Inventory folder not created");
      }
    }

    mergeConfigFiles("config.yml");
    mergeConfigFiles("sounds.yml");
    mergeConfigFiles("messages.yml");
    mergeConfigFiles("HyponosisWords.yml");
    mergeConfigFiles("welcomebook.yml");
    mergeConfigFiles("nanny_messages.yml");
    mergeConfigFiles("nanny_personalities.yml");
    mergeConfigFiles("nanny_behavior_words.yml");
    mergeConfigFiles("integrations.yml");
    mergeConfigFiles("StoryNook1.2.4.zip");
    try {
        String configured = getConfig().getString("Crypto.Key_Path", "");
        if (configured == null) configured = "";
        configured = expandEnvVars(configured.trim());
        java.io.File keyFile;
        if (configured.isEmpty()) {
            keyFile = new java.io.File(getDataFolder(), ".crypto.key");
        } else {
            keyFile = new java.io.File(configured);
        }
        cryptoService = com.storynook.nanny.crypto.CryptoService.initialize(keyFile, getLogger());
    } catch (Exception e) {
        getLogger().severe("[Crypto] Failed to initialize key store: " + e.getMessage());
        getLogger().severe("[Crypto] Disabling plugin to prevent data corruption.");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
    loadGlobalConfig();
    loadIntegrationsConfig();
    integrationsBus = new com.storynook.Integrations.IntegrationsBus(this);

    if (getServer().getPluginManager().getPlugin("Jobs") != null) {
        getLogger().info("Jobs Reborn detected; registering JobsRebornHook.");
        getServer().getPluginManager().registerEvents(
                new com.storynook.Integrations.jobs.JobsRebornHook(this), this);
    }
    if (getServer().getPluginManager().getPlugin("AdvancedJobs") != null) {
        getLogger().info("AdvancedJobs detected; registering AdvancedJobsHook.");
        getServer().getPluginManager().registerEvents(
                new com.storynook.Integrations.jobs.AdvancedJobsHook(this), this);
    }
    if (getServer().getPluginManager().getPlugin("BeautyQuests") != null) {
        getLogger().info("BeautyQuests detected; registering BeautyQuestsHook.");
        getServer().getPluginManager().registerEvents(
                new com.storynook.Integrations.beautyquests.BeautyQuestsHook(this), this);
    }

    LoadStats.setPlugin(this);
    LoadSelectedSounds.setPlugin(this);
    SavePlayerStats.setPlugin(this);
    CreateDefaultStats.setPlugin(this);
    UpdateStats();
    playerStatsMap = new HashMap<UUID, PlayerStats>();
    
    //Menus
    SettingsMenu SettingsMenu = new SettingsMenu(this);
    Caregivermenu caregivermenu = new Caregivermenu(this);
    HUDMenu hudmenu = new HUDMenu(this);
    IncontinenceMenu incontinencemenu = new IncontinenceMenu(this);
    SoundEffectsMenu soundmenu = new SoundEffectsMenu(this);

    // Nanny system — only wired when Citizens2 is fully loaded.
    if (citizensEnabled) {
        NannyMenu nannymenu = new NannyMenu(this);
        nannyManager = new NannyManager(this);
        nannyManager.init();
        diaperPunishment = new com.storynook.nanny.DiaperPunishment(this);
        diaperPunishment.start();
        BehaviorScoreboard behaviorScoreboard = new BehaviorScoreboard();
        BehaviorSignals behaviorSignals = new BehaviorSignals(this, behaviorScoreboard, nannyManager);
        getServer().getPluginManager().registerEvents(behaviorSignals, this);
        nannyManager.setBehaviorScoreboard(behaviorScoreboard);
        DisciplineDispatcher disciplineDispatcher = new DisciplineDispatcher(
                this, behaviorScoreboard, nannyManager.getCareEngine());
        nannyManager.setDisciplineDispatcher(disciplineDispatcher);
        getServer().getPluginManager().registerEvents(nannymenu, this);
        getServer().getPluginManager().registerEvents(nannyManager, this);
        NannyChatEngine nannyChatEngine = nannyManager.getChatEngine();
        if (nannyChatEngine != null) {
            getServer().getPluginManager().registerEvents(nannyChatEngine, this);
        }
        if (this.VentureChat) {
            getServer().getPluginManager().registerEvents(new NannyVentureChatHook(this), this);
        }
        getServer().getPluginManager().registerEvents(
                new com.storynook.nanny.ArmorLockListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.storynook.nanny.RoomLockListener(this), this);
        com.storynook.nanny.NannyReactiveListener reactiveListener =
                new com.storynook.nanny.NannyReactiveListener(this);
        getServer().getPluginManager().registerEvents(reactiveListener, this);
        nannyManager.setReactiveListener(reactiveListener);
        nannyCommand = new NannyCommand(this);
    }

    //listeners
    PlayerEventListener playerEventListener = new PlayerEventListener(this);
    Sit sit = new Sit(this);
    Toilet toilet = new Toilet(this);
    PantsCrafting pantsCrafting = new PantsCrafting(this);
    AccidentsRandom accident = new AccidentsRandom(this);
    Changing change = new Changing(this);
    washer washer = new washer(this);
    // LeashControl leashControl = new LeashControl(this);
    ArmorStandProtection armorprotect = new ArmorStandProtection(this);
    FeedingAction feed = new FeedingAction(this);
    DiaperPail pail = new DiaperPail(this);
    TickleAccidents tickle = new TickleAccidents(this);
    CustomFurnitureRemoval customFurnitureRemoval = new CustomFurnitureRemoval();
    cribs crib = new cribs(this);
    Laxative lax = new Laxative(this);
    BindingDiaper binding = new BindingDiaper(this);
    com.storynook.Event_Listeners.PaciBinding paciBinding =
        new com.storynook.Event_Listeners.PaciBinding(this);
    Hypno hypno = new Hypno(this);
    PlayerInteract playerInteract = new PlayerInteract(this);
    PlayerInteractWithEntity playerInteractWithEntity = new PlayerInteractWithEntity(this);

    //Create all the custom recipes and items related
    ItemManager itemManager = new ItemManager(this);
    underwear underwear = new underwear(this);
    pants pants = new pants(this);
    Nanny nanny = new Nanny(this);

    itemManager.createAllRecipes();
    underwear.createAllRecipes();
    pants.createCleanPantsRecipe();
    pants.WashedPants();
    crib.createCribRecipe();
    // itemManager.createLaxRecipe();
    CribPlacement placement = new CribPlacement(this);
   
    // Create an array of all your listener objects
    Object[] listeners = new Object[]{ playerEventListener, 
      SettingsMenu, 
      caregivermenu, 
      incontinencemenu, 
      hudmenu, 
      feed, 
      soundmenu, 
      armorprotect, 
      pantsCrafting, 
      washer, 
      sit, 
      toilet, 
      accident, 
      lax, 
      change, 
      placement, 
      pail, 
      customFurnitureRemoval, 
      tickle, 
      binding,
      paciBinding,
      hypno,
      playerInteract,
      playerInteractWithEntity
    };

    // Loop through and register each listener
    for (Object listener : listeners) {
        if (listener instanceof Listener) {
            getServer().getPluginManager().registerEvents((Listener) listener, this);
        }
    }

    // --- Furniture / crib redesign ---
    this.cribPdcKeys = new com.storynook.furniture.CribPdcKeys(this);
    this.cribRegistry = new com.storynook.furniture.CribRegistry();
    this.knockbackTracker = new com.storynook.furniture.KnockbackTracker();
    this.carryManager = new com.storynook.furniture.carry.CarryManager(this);

    org.bukkit.event.Listener[] furnitureListeners = new org.bukkit.event.Listener[] {
        new com.storynook.furniture.CribListener(this, cribRegistry, cribPdcKeys),
        new com.storynook.furniture.ContainmentEventListener(this, cribRegistry, knockbackTracker),
        carryManager,
        new com.storynook.furniture.carry.CarryPickupListener(this, carryManager),
        new com.storynook.furniture.carry.CarryDropListener(this, carryManager, cribRegistry, cribPdcKeys),
    };
    for (org.bukkit.event.Listener l : furnitureListeners) {
        getServer().getPluginManager().registerEvents(l, this);
    }

    // Start the soft-containment task (every 2 ticks)
    new com.storynook.furniture.CribContainmentTask(this, cribRegistry, knockbackTracker)
        .runTaskTimer(this, 2L, 2L);

    // --- Highchair subsystem ---
    com.storynook.furniture.highchair.HighchairRegistry.init();
    this.highchairRegistry = new com.storynook.furniture.highchair.HighchairRegistry();
    com.storynook.furniture.highchair.HighchairPdcKeys highchairKeys =
        new com.storynook.furniture.highchair.HighchairPdcKeys(this);
    this.highchairListener = new com.storynook.furniture.highchair.HighchairListener(this, highchairRegistry, highchairKeys);
    com.storynook.furniture.highchair.HighchairCraftingListener highchairCrafting =
        new com.storynook.furniture.highchair.HighchairCraftingListener(this);
    getServer().getPluginManager().registerEvents(highchairListener, this);
    getServer().getPluginManager().registerEvents(highchairCrafting, this);
    if (Boolean.TRUE.equals(globalConfig.get("Nursery_Items"))) {
        highchairCrafting.register();
    }

    // Re-register any already-loaded highchair Interactions in spawn chunks.
    for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
        for (org.bukkit.Chunk ch : w.getLoadedChunks()) {
            for (org.bukkit.entity.Entity e : ch.getEntities()) {
                if (!(e instanceof org.bukkit.entity.Interaction inter)) continue;
                if (!inter.getScoreboardTags().contains(
                        com.storynook.furniture.highchair.HighchairPdcKeys.SCOREBOARD_TAG)) continue;
                com.storynook.furniture.highchair.Highchair h =
                    com.storynook.furniture.highchair.Highchair.fromPdc(inter, highchairKeys);
                if (h != null) highchairRegistry.register(h);
            }
        }
    }

    // --- Changing table subsystem ---
    com.storynook.furniture.changingtable.ChangingTableRegistry.init();
    this.changingTableRegistry = new com.storynook.furniture.changingtable.ChangingTableRegistry();
    this.changingTablePdcKeys = new com.storynook.furniture.changingtable.ChangingTablePdcKeys(this);
    this.changingTableListener = new com.storynook.furniture.changingtable.ChangingTableListener(
        this, this.changingTableRegistry, this.changingTablePdcKeys);
    com.storynook.furniture.changingtable.ChangingTableCraftingListener changingTableCrafting =
        new com.storynook.furniture.changingtable.ChangingTableCraftingListener(this);
    getServer().getPluginManager().registerEvents(this.changingTableListener, this);
    this.changingTableListener.startViewerTask(this);
    getServer().getPluginManager().registerEvents(changingTableCrafting, this);
    this.changingTableInventoryManager =
        new com.storynook.furniture.changingtable.ChangingTableInventoryManager(this);
    getServer().getPluginManager().registerEvents(this.changingTableInventoryManager, this);
    if (Boolean.TRUE.equals(globalConfig.get("Nursery_Items"))) {
        com.storynook.furniture.changingtable.ChangingTableRecipes.register(this);
    }

    // Re-register any already-loaded changing-table Interactions in spawn chunks.
    for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
        for (org.bukkit.Chunk ch : w.getLoadedChunks()) {
            for (org.bukkit.entity.Entity e : ch.getEntities()) {
                if (!(e instanceof org.bukkit.entity.Interaction inter)) continue;
                if (!inter.getScoreboardTags().contains(
                        com.storynook.furniture.changingtable.ChangingTablePdcKeys.SCOREBOARD_TAG)) continue;
                com.storynook.furniture.changingtable.ChangingTable t =
                    com.storynook.furniture.changingtable.ChangingTable.fromPdc(inter, this.changingTablePdcKeys);
                if (t != null) this.changingTableRegistry.register(t);
            }
        }
    }

    // Walk already-loaded chunks so cribs in spawn chunks are registered immediately
    for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
        for (org.bukkit.Chunk ch : w.getLoadedChunks()) {
            for (org.bukkit.entity.Entity e : ch.getEntities()) {
                if (!(e instanceof org.bukkit.entity.Interaction inter)) continue;
                if (!inter.getScoreboardTags().contains(com.storynook.furniture.CribPdcKeys.SCOREBOARD_TAG)) continue;
                com.storynook.furniture.Crib c = com.storynook.furniture.Crib.fromPdc(inter, cribPdcKeys);
                if (c != null) cribRegistry.register(c);
            }
        }
    }

    loadSounds();
    loadMessages();
    loadHypnoWordsConfig();

    //Books for instructions
    tutorialbook = new TutorialBook(this);

    

    //Registers the commands

    String[] singleCommands = {"settings", "pee", "poop", "stats", "nightvision", "nv", "diaperreload", "welcomebook"};
    for (String cmd : singleCommands) {
        getCommand(cmd).setExecutor(commandHandler);
    }

    java.util.List<String> dualCommands = new java.util.ArrayList<>(java.util.Arrays.asList(
        "debug", "check", "caregiver", "lockincon", "unlockincon", "minfill", "equiparmor", "change", "hypno"));
    if (citizensEnabled) {
        dualCommands.add("nanny");
    }
    for (String cmd : dualCommands) {
        getCommand(cmd).setExecutor(commandHandler);
        getCommand(cmd).setTabCompleter(commandHandler);
    }

    //Score board setup
    manager = Bukkit.getScoreboardManager();
    UpdateStatsBar();
  }

  private String expandEnvVars(String value) {
      if (value == null) return null;
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(value);
      StringBuffer sb = new StringBuffer();
      while (m.find()) {
          String env = System.getenv(m.group(1));
          m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(env == null ? "" : env));
      }
      m.appendTail(sb);
      return sb.toString();
  }

  /**
   * Reads a config string. If stored as plaintext, encrypts it in-place and saves config.yml.
   * Returns the plaintext value. Returns "" for null/empty.
   */
  public String readEncryptedConfigString(String path) {
      String raw = getConfig().getString(path, "");
      if (raw == null || raw.isEmpty()) return "";
      if (cryptoService != null && cryptoService.isEncrypted(raw)) {
          return cryptoService.decrypt(raw);
      }
      if (cryptoService != null && !raw.isEmpty()) {
          String enc = cryptoService.encrypt(raw);
          getConfig().set(path, enc);
          saveConfig();
      }
      return raw;
  }

  public com.storynook.nanny.MembershipProvider buildMembershipProvider() {
      if (!getConfig().getBoolean("Nanny.Membership.enabled", false)) {
          return new com.storynook.nanny.AlwaysLockedProvider();
      }

      com.storynook.nanny.membership.CompositeMembershipProvider composite =
              new com.storynook.nanny.membership.CompositeMembershipProvider();

      if (getConfig().getBoolean("Nanny.Membership.Permission.enabled", false)) {
          String node = getConfig().getString("Nanny.Membership.Permission.node",
                  "accidentprone.nanny.ai_unlocked");
          composite.add(new com.storynook.nanny.membership.PermissionMembershipProvider(node));
      }

      String redirectUri = getConfig().getString("Nanny.Membership.Redirect_URI",
              "https://accident-prone.io/oauth-redirect");

      if (oauthHelper == null) {
          oauthHelper = new com.storynook.nanny.membership.OAuthHelper();
      }

      if (getConfig().getBoolean("Nanny.Membership.Patreon.enabled", false)) {
          String cid = getConfig().getString("Nanny.Membership.Patreon.Client_ID", "");
          String csec = readEncryptedConfigString("Nanny.Membership.Patreon.Client_Secret");
          java.util.List<String> tiers = getConfig().getStringList("Nanny.Membership.Patreon.Tier_Required");
          String campaignId = getConfig().getString("Nanny.Membership.Patreon.Campaign_ID", "");
          composite.add(new com.storynook.nanny.membership.PatreonMembershipProvider(
                  this, oauthHelper, cid, csec, redirectUri, tiers, campaignId));
      }
      if (getConfig().getBoolean("Nanny.Membership.Subscribestar.enabled", false)) {
          String cid = getConfig().getString("Nanny.Membership.Subscribestar.Client_ID", "");
          String csec = readEncryptedConfigString("Nanny.Membership.Subscribestar.Client_Secret");
          java.util.List<String> tiers = getConfig().getStringList("Nanny.Membership.Subscribestar.Tier_Required");
          composite.add(new com.storynook.nanny.membership.SubscribestarMembershipProvider(
                  this, oauthHelper, cid, csec, redirectUri, tiers));
      }

      if (composite.isEmpty()) return new com.storynook.nanny.AlwaysLockedProvider();
      return composite;
  }

  public void mergeConfigFiles(String fileName) {
    File dataFile = new File(getDataFolder(), fileName);

    // Binary archives (.zip etc.) are not mergeable as YAML. Always overwrite
    // with the JAR's bundled copy so the data folder tracks the latest build,
    // and so a previous bug that wrote empty YAML over the .zip can self-heal.
    boolean isBinary = fileName.toLowerCase().endsWith(".zip");

    try {
        if (!dataFile.exists() || isBinary) {
            // Create or overwrite from JAR resources (binary-safe).
            InputStream resourceStream = this.getClass().getClassLoader()
                    .getResourceAsStream(fileName);

            if (resourceStream == null) {
                getLogger().severe("Could not find " + fileName + " in resources!");
                return;
            }

            try (OutputStream fileOutputStream = new FileOutputStream(dataFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = resourceStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                if (isBinary) {
                    getLogger().info("Refreshed " + fileName + " from JAR");
                } else {
                    getLogger().info("Created " + fileName);
                }
            } finally {
                if (resourceStream != null) {
                    resourceStream.close();
                }
            }
        } else {
            // If file exists, merge new values, overwriting existing ones
            YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(dataFile);

            InputStream resourceStream = this.getClass().getClassLoader()
                    .getResourceAsStream(fileName);

            if (resourceStream == null) {
                getLogger().severe("Could not find " + fileName + " in resources!");
                return;
            }

            try {
                // Read the resource stream into a temporary file
                File tempFile = new File(getDataFolder(), "temp_" + fileName);
                try (OutputStream tempOutputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = resourceStream.read(buffer)) != -1) {
                        tempOutputStream.write(buffer, 0, bytesRead);
                    }
                }

                YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);

                // Merge new configuration into existing one
                // This will overwrite existing entries with the new values
                for (String key : newConfig.getKeys(true)) {
                    if (!existingConfig.contains(key)) {
                        existingConfig.set(key, newConfig.get(key));
                    }
                }

                // Save merged configuration back to file
                existingConfig.save(dataFile);
                getLogger().info("Updated " + fileName);

                // Clean up temporary file
                tempFile.delete();
            } finally {
                resourceStream.close();
            }
        }
    } catch (IOException e) {
        getLogger().severe("Error handling " + fileName + ": " + e.getLocalizedMessage());
        e.printStackTrace();
    }
  }

  @Override
  public void onDisable()
  {
    for (BukkitTask task : ParticleEffects.values()) {
        if (task != null) {
            task.cancel();
        }
    }
    if (this.changingTableListener != null) {
        this.changingTableListener.stopViewerTask();
        this.changingTableListener.releaseAllOnDisable();
    }
    saveAllPlayerStats();
  }

  private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        
        // If the file doesn't exist, save the default one
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getRandomMessage(String category) {
        Random random = new Random();
        
        List<String> messages = messagesConfig.getStringList(category);
        
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        int randomIndex = random.nextInt(messages.size());
        return messages.get(randomIndex);
    }
    public String getRandomMessage(String category, String... placeholders) {
        Random random = new Random();
        List<String> messages = messagesConfig.getStringList(category);
        
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        int randomIndex = random.nextInt(messages.size());
        String message = messages.get(randomIndex);
        
        // Replace placeholders
        for (int i = 0; i < placeholders.length && i < 20; i += 2) { // Limit to 10 replacements
            if (i + 1 < placeholders.length) {
                String placeholder = placeholders[i];
                String replacement = placeholders[i + 1];
                
                // Ensure we're not dealing with null values
                if (placeholder != null && replacement != null && message != null) {
                    message = message.replace(placeholder, replacement);
                }
            }
        }
        
        return message;
    }

  public void loadSounds() {
    try {
        File soundsFile = new File(getDataFolder(), "sounds.yml");

        // Create default file if it doesn't exist
        if (!soundsFile.exists()) {
            saveResource("sounds. yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(soundsFile);

        // Load each category and its sounds into the hashmap
        for (String category : config.getKeys(false)) {
            Map<String, Boolean> categoryMap = new HashMap<>();

            List<String> sounds = config.getStringList(category);
            if (!sounds.isEmpty()) {
                for (String sound : sounds) {
                    categoryMap.put(sound, true);
                    getLogger().info("Loaded sound: " + sound + " under category: " + category);
                }
            } else {
                getLogger().warning("No sounds found in category: " + category);
            }

            // Put the category map into the main map
            soundConfig.put(category, categoryMap);
        }

        if (soundConfig.isEmpty()) {
            getLogger().severe("No sounds loaded. Please check your sounds.yml file.");
        } else {
            getLogger().info("Successfully loaded " + soundConfig.size() + " categories of sounds.");
            for (Map.Entry<String, Map<String, Boolean>> entry : soundConfig.entrySet()) {
                String category = entry.getKey();
                int soundCount = entry.getValue().size();
                getLogger().info("Category: " + category + ", Sounds loaded: " + soundCount);
            }
        }

    } catch (Exception e) {
        getLogger().severe("Error loading sounds.yml: " + e.getMessage());
        e.printStackTrace();
    }
  }
  public void loadGlobalConfig() {
    try {
        File configFile = new File(getDataFolder(), "config.yml");

        // Create default file if it doesn't exist
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Initialize the globalConfig HashMap if it hasn't been initialized yet
        if (globalConfig == null) {
            globalConfig = new HashMap<>();
        }

        // Load the Accidents boolean
        boolean accidentsEnabled = config.getBoolean("Accidents", false);
        globalConfig.put("Accidents", accidentsEnabled);

        // Load the Diapers boolean
        boolean diapersEnabled = config.getBoolean("Diapers", false);
        globalConfig.put("Diapers", diapersEnabled);

        boolean nurseryItemsEnabled = config.getBoolean("Nursery_Items", true);
        globalConfig.put("Nursery_Items", nurseryItemsEnabled);

        // Load the Messing boolean
        boolean messingEnabled = config.getBoolean("Settings_Menu.Messing", false);
        globalConfig.put("Messing", messingEnabled);

        // Load the Caregiver boolean
        boolean CaregiverEnabled = config.getBoolean("Settings_Menu.Caregivers", false);
        globalConfig.put("Caregivers", CaregiverEnabled);

        // Load the Crib New System kill-switch (default true = new system enabled)
        boolean cribNewSystemEnabled = config.getBoolean("Settings_Menu.Crib_New_System", true);
        globalConfig.put("Crib_New_System", cribNewSystemEnabled);

        // Load the ShowUndies boolean
        boolean showUndiesEnabled = config.getBoolean("Settings_Menu.Show_Undies", false);
        globalConfig.put("Show_Undies", showUndiesEnabled);

        // Load the Incontinence boolean
        boolean IncontinenceEnabled = config.getBoolean("Settings_Menu.Incontinence", false);
        globalConfig.put("Incontinence", IncontinenceEnabled);

        // Load the Hardcore boolean
        boolean HardcoreEnabled = config.getBoolean("Settings_Menu.Hardcore", false);
        globalConfig.put("Hardcore", HardcoreEnabled);

        boolean membershipEnabled = config.getBoolean("Settings_Menu.Membership", false);
        globalConfig.put("Membership", membershipEnabled);

        // Load the Lock Optin on Hardcore boolean
        boolean LockOptinHardcoreEnabled = config.getBoolean("Lock_Optin_with_Hardcore", false);
        globalConfig.put("Lock_Optin_with_Hardcore", LockOptinHardcoreEnabled);

        // Balance display on sidebar scoreboard (top line, requires PlaceholderAPI)
        globalConfig.put("Show_Balance", config.getBoolean("Show_Balance", true));
        globalConfig.put("Balance_Placeholder", config.getString("Balance_Placeholder", "%vault_eco_balance%"));

        // Load the MinFill boolean
        boolean MinFillEnabled = config.getBoolean("Settings_Menu.MinFilltoggle", false);
        globalConfig.put("MinFilltoggle", MinFillEnabled);

        // Server-default warning threshold (root key, not gated by Settings_Menu)
        globalConfig.put("MinFill", config.getInt("MinFill", 30));

        // Toilet relief tuning -- see Toilet.canRelieveOnToilet
        globalConfig.put("Toilet_Warning_Window_Max_Seconds", config.getDouble("Toilet.Warning_Window_Max_Seconds", 60.0));
        globalConfig.put("Toilet_Warning_Window_Min_Seconds", config.getDouble("Toilet.Warning_Window_Min_Seconds", 5.0));

        // Warning probability scaling -- see Warnings.handleWarning
        globalConfig.put("Warnings_Probability_Max", config.getDouble("Warnings.Probability_Max", 1.0));
        globalConfig.put("Warnings_Probability_Min", config.getDouble("Warnings.Probability_Min", 0.001));

        // Rash system
        globalConfig.put("Rash_Mode", config.getString("Rash.mode", "poison"));
        globalConfig.put("Rash_Threshold", config.getDouble("Rash.threshold", 250.0));
        globalConfig.put("Rash_Amount", config.getDouble("Rash.amount", 1.0));
        globalConfig.put("Rash_Slowness", config.getBoolean("Rash.rash_slowness", true));

        // Load URLs
        String DiscordURL = config.getString("Links.Discord_Link", null);
        globalConfig.put("Discord_Link", DiscordURL);

        String PatreonURL = config.getString("Links.Patreon_Link", null);
        globalConfig.put("Patreon_Link", PatreonURL);

        String SubscribestarURL = config.getString("Links.Subscribestar_Link", null);
        globalConfig.put("Subscribestar_Link", SubscribestarURL);

        // Load the Binding_Diapers boolean
        boolean bindingDiapers = config.getBoolean("Secret_Menu.Binding_Diapers", false);
        globalConfig.put("Binding_Diapers", bindingDiapers);

        // Load the Hypnosis boolean
        boolean Hypnosis = config.getBoolean("Secret_Menu.Hypnosis", false);
        globalConfig.put("Hypno", Hypnosis);

        long hypnoDurationDays = config.getLong("Secret_Menu.Hypno_Duration_Days", 3L);
        globalConfig.put("Hypno_Duration_Days", hypnoDurationDays);

        int hypnoMaxTriggers = config.getInt("Secret_Menu.Hypno_Max_Triggers", 0);
        globalConfig.put("Hypno_Max_Triggers", hypnoMaxTriggers);

        // Load the Nanny block
        boolean nannyEnabled = config.getBoolean("Nanny.enabled", false);
        globalConfig.put("Nanny", nannyEnabled);
        globalConfig.put("Nanny_Owner_Timeout_Days", config.getLong("Nanny.Owner_Timeout_Days", 14L));
        globalConfig.put("Nanny_Max_Server_Nannies", config.getInt("Nanny.Max_Server_Nannies", 50));
        globalConfig.put("Nanny_Default_Home_Radius", config.getInt("Nanny.Default_Home_Radius", 50));
        globalConfig.put("Nanny_Default_Mood", config.getString("Nanny.Default_Mood", "CARING"));
        globalConfig.put("Nanny_Default_Chest_Mode", config.getString("Nanny.Default_Chest_Mode", "INVENTORY_ONLY"));
        globalConfig.put("Nanny_Default_Crafting_Mode", config.getString("Nanny.Default_Crafting_Mode", "BASIC"));
        globalConfig.put("Nanny_Default_Change_Threshold", config.getInt("Nanny.Default_Change_Threshold", 70));
        globalConfig.put("Nanny_Default_Feed_Threshold", config.getInt("Nanny.Default_Feed_Threshold", 14));
        globalConfig.put("Nanny_Default_Hydration_Threshold", config.getInt("Nanny.Default_Hydration_Threshold", 30));
        globalConfig.put("Nanny_Chat_enabled", config.getBoolean("Nanny.Chat.enabled", true));
        globalConfig.put("Nanny_Chat_Default_Tier", config.getString("Nanny.Chat.Default_Tier", "BASIC"));
        globalConfig.put("Nanny_Chat_Default_Respond_To", config.getString("Nanny.Chat.Default_Respond_To", "OWNER"));
        globalConfig.put("Nanny_Chat_Local_Radius", config.getInt("Nanny.Chat.Local_Radius", 30));
        globalConfig.put("Nanny_Chat_AI_Provider", config.getString("Nanny.Chat.AI.Provider", "OPENAI"));
        globalConfig.put("Nanny_Chat_AI_API_Key", readEncryptedConfigString("Nanny.Chat.AI.API_Key"));
        globalConfig.put("Nanny_Chat_AI_Model", config.getString("Nanny.Chat.AI.Model", "gpt-4o-mini"));
        globalConfig.put("Nanny_Chat_AI_Required_Membership_Tier", config.getString("Nanny.Chat.AI.Required_Membership_Tier", ""));
        globalConfig.put("Nanny_Chat_AI_Cooldown_Seconds", config.getInt("Nanny.Chat.AI.Chat_Cooldown_Seconds", 30));
        globalConfig.put("Nanny_Chat_AI_Context_Event_Count", config.getInt("Nanny.Chat.AI.Context_Event_Count", 25));
        globalConfig.put("Nanny_Chat_AI_Context_Chat_Count", config.getInt("Nanny.Chat.AI.Context_Chat_Count", 10));
        globalConfig.put("Nanny_Chat_AI_Voice_Sample_Count", config.getInt("Nanny.Chat.AI.Voice_Sample_Count", 6));
        globalConfig.put("Nanny_Chat_AI_Max_Tokens", config.getInt("Nanny.Chat.AI.Max_Tokens", 4000));
        globalConfig.put("Nanny_Chat_Min_Words", config.getInt("Nanny.Chat.Min_Words", 3));
        globalConfig.put("Nanny_Chat_Ambient_Chance", config.getInt("Nanny.Chat.Ambient_Chance", 1));
        globalConfig.put("Nanny_Chat_VC_Channel_Name", config.getString("Nanny.Chat.VC_Channel_Name", "Local"));
        globalConfig.put("Nanny_Chat_AI_Endpoint", config.getString("Nanny.Chat.AI.Endpoint", ""));
        globalConfig.put("Nanny_Chat_AI_System_Prompt", config.getString("Nanny.Chat.AI.System_Prompt", ""));
        globalConfig.put("Nanny_Behavior_enabled", config.getBoolean("Nanny.Behavior.enabled", true));
        globalConfig.put("Nanny_Behavior_Score_Decay_Per_MCDay", config.getInt("Nanny.Behavior.Score_Decay_Per_MCDay", 1));
        globalConfig.put("Nanny_Behavior_Score_Threshold_Warn", config.getInt("Nanny.Behavior.Score_Threshold_Warn", -20));
        globalConfig.put("Nanny_Behavior_Score_Threshold_Moderate", config.getInt("Nanny.Behavior.Score_Threshold_Moderate", -40));
        globalConfig.put("Nanny_Behavior_Score_Threshold_Serious", config.getInt("Nanny.Behavior.Score_Threshold_Serious", -65));
        globalConfig.put("Nanny_Behavior_Score_Threshold_Severe", config.getInt("Nanny.Behavior.Score_Threshold_Severe", -90));
        globalConfig.put("Nanny_Behavior_Score_Floor", config.getInt("Nanny.Behavior.Score_Floor", -100));
        globalConfig.put("Nanny_Behavior_Discipline_Cooldown_Minutes", config.getInt("Nanny.Behavior.Discipline_Cooldown_Minutes", 5));
        globalConfig.put("Nanny_Behavior_Diaper_Punishment_Min_Days", config.getInt("Nanny.Behavior.Diaper_Punishment_Min_Days", 1));
        globalConfig.put("Nanny_Behavior_Diaper_Punishment_Max_Days", config.getInt("Nanny.Behavior.Diaper_Punishment_Max_Days", 30));
        globalConfig.put("Nanny_Behavior_Diaper_Punishment_Violations_Before_Escalation", config.getInt("Nanny.Behavior.Diaper_Punishment_Violations_Before_Escalation", 3));
        globalConfig.put("Nanny_Behavior_Praise_Grace_Seconds", config.getInt("Nanny.Behavior.Praise_Grace_Seconds", 300));
        globalConfig.put("Nanny_Behavior_Sycophancy_Score_Threshold", config.getInt("Nanny.Behavior.Sycophancy_Score_Threshold", 0));
        globalConfig.put("Nanny_Behavior_Sycophancy_Streak_Threshold", config.getInt("Nanny.Behavior.Sycophancy_Streak_Threshold", 20));
        globalConfig.put("Nanny_Behavior_Sycophancy_Dampen_Pct", config.getInt("Nanny.Behavior.Sycophancy_Dampen_Pct", 50));
        globalConfig.put("Nanny_Behavior_Good_Behavior_Day_Off_Threshold", config.getInt("Nanny.Behavior.Good_Behavior_Day_Off_Threshold", 20));

        getLogger().info("Successfully loaded global config.");

    } catch (Exception e) {
        getLogger().severe("Error loading config.yml: " + e.getMessage());
        e.printStackTrace();
    }
  }
  public void loadIntegrationsConfig() {
    try {
        File configFile = new File(getDataFolder(), "integrations.yml");
        if (!configFile.exists()) {
            saveResource("integrations.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        if (integrationsConfig == null) integrationsConfig = new HashMap<>();
        integrationsConfig.put("Integrations_enabled", cfg.getBoolean("enabled", false));

        integrationsConfig.put("Caregiver_Min_Wetness", cfg.getInt("Events.Caregiver.Min_Wetness", 30));
        integrationsConfig.put("Caregiver_Min_Fullness", cfg.getInt("Events.Caregiver.Min_Fullness", 30));
        integrationsConfig.put("Caregiver_Feed_Below", cfg.getInt("Events.Caregiver.Feed_Below", 70));
        integrationsConfig.put("Cooldown_Change_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Change_Seconds", 60));
        integrationsConfig.put("Cooldown_Feed_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Feed_Seconds", 120));
        integrationsConfig.put("Cooldown_Equip_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Equip_Seconds", 300));
        integrationsConfig.put("Cooldown_Carry_Pickup_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Carry_Pickup_Seconds", 10));
        integrationsConfig.put("Cooldown_Carry_Drop_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Carry_Drop_Seconds", 10));
        integrationsConfig.put("Cooldown_Highchair_Place_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Highchair_Place_Seconds", 10));
        integrationsConfig.put("Cooldown_Change_On_Table_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Change_On_Table_Seconds", 60));
        integrationsConfig.put("Cooldown_Stock_Changing_Table_Seconds", cfg.getInt("Events.Caregiver.Cooldown_Stock_Changing_Table_Seconds", 1));

        integrationsConfig.put("Cooldown_Pail_Fill_Seconds", cfg.getInt("Events.Crafter.Cooldown_Pail_Fill_Seconds", 30));
        integrationsConfig.put("Cooldown_Wash_Pants_Seconds", cfg.getInt("Events.Crafter.Cooldown_Wash_Pants_Seconds", 15));

        integrationsConfig.put("Cooldown_Toilet_Relief_Seconds", cfg.getInt("Events.Little.Cooldown_Toilet_Relief_Seconds", 90));
        integrationsConfig.put("Cooldown_Accident_Handled_Seconds", cfg.getInt("Events.Little.Cooldown_Accident_Handled_Seconds", 180));
        integrationsConfig.put("Cooldown_Hydrate_Threshold_Seconds", cfg.getInt("Events.Little.Cooldown_Hydrate_Threshold_Seconds", 600));
        integrationsConfig.put("Hydrate_Threshold", cfg.getInt("Events.Little.Hydrate_Threshold", 80));

        integrationsConfig.put("Jobs_enabled", cfg.getBoolean("Jobs.enabled", false));
        integrationsConfig.put("BeautyQuests_enabled", cfg.getBoolean("BeautyQuests.enabled", false));

        jobsActionMap.clear();
        org.bukkit.configuration.ConfigurationSection ja = cfg.getConfigurationSection("Jobs.Action_Map");
        if (ja != null) {
            for (String key : ja.getKeys(false)) {
                jobsActionMap.put(key, ja.getString(key, ""));
            }
        }

        beautyQuestsTriggerMap.clear();
        org.bukkit.configuration.ConfigurationSection bq = cfg.getConfigurationSection("BeautyQuests.Quest_Trigger_Map");
        if (bq != null) {
            for (String key : bq.getKeys(false)) {
                beautyQuestsTriggerMap.put(key, bq.getString(key, ""));
            }
        }

        getLogger().info("Successfully loaded integrations config.");
    } catch (Exception e) {
        getLogger().severe("Error loading integrations.yml: " + e.getMessage());
        e.printStackTrace();
    }
  }
  public void loadHypnoWordsConfig() {
    try {
        File configFile = new File(getDataFolder(), "HyponosisWords.yml");

        // Create default file if it doesn't exist
        if (!configFile.exists()) {
            saveResource("HyponosisWords.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        hypnoWords = new ArrayList<>();

        if (config.isList("words")) {
            List<String> words = config.getStringList("words");
            hypnoWords.addAll(words);
        } else {
            // If no "words" list, load all root-level strings as words
            for (String key : config.getKeys(false)) {
                Object value = config.get(key);
                if (value instanceof String) {
                    hypnoWords.add((String) value);
                }
            }
        }

        getLogger().info("Successfully loaded " + hypnoWords.size() + " hyponosis words.");

    } catch (Exception e) {
        getLogger().severe("Error loading HyponosisWords.yml:  " + e.getMessage());
        e.printStackTrace();
    }
  }
  public String getRandomHypnoWord() {
    if (hypnoWords == null || hypnoWords.isEmpty()) {
        return null;
    }
    Random random = new Random();
    return hypnoWords.get(random.nextInt(hypnoWords.size()));
  }
  public List<String> getHypnoWords() {
    return hypnoWords != null ? new ArrayList<>(hypnoWords) : new ArrayList<>();
  }

  public void loadPlayerStats(Player player) {
    getLogger().warning("Player Joined " + player.getName());
    UUID playerUUID = player.getUniqueId();
    PlayerStats stats = new PlayerStats(playerUUID, this);
    File playerFile = getPlayerFile(playerUUID);
    
    if (playerFile.exists()) {
        getLogger().info("Loading stats for player " + player.getName());
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        LoadStats.loadPlayerStatsFromConfig(stats, config);
    } else {
        getLogger().info("No stats file found for player " + player.getName() + ". Creating default stats.");
        CreateDefaultStats.createDefaultPlayerStats(stats, player);
        tutorialbook.giveWelcomeBook(player);
    }
    
    playerStatsMap.put(playerUUID, stats);
    
    if (stats.getOptin() && stats.getUI() > 0) {
        ScoreBoard.createSidebar(player);
    }
}

public Boolean parseBooleanValue(Object value, Boolean defaultValue) {
    if (value instanceof Boolean) {
        return (Boolean) value;
    } else if (value instanceof String) {
        String strVal = (String) value;
        if (strVal.equalsIgnoreCase("true")) {
            return true;
        } else if (strVal.equalsIgnoreCase("false")) {
            return false;
        } else {
            return Boolean.parseBoolean(strVal);
        }
    } else {
        try {
            int intValue = Integer.parseInt(value.toString());
            return intValue > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

  public File getPlayerFile(UUID playerUUID) {
    return new File(getDataFolder(), "players" + File.separator + playerUUID.toString() + ".yml");
  }
  public PlayerStats getPlayerStats(UUID playerUUID) {
    return playerStatsMap.get(playerUUID);
  }
  private void saveAllPlayerStats() {
        for (UUID playerUUID : playerStatsMap.keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                SavePlayerStats.savePlayerStats(player);
            }
        }
  }

  private void UpdateStats() {
    Bukkit.getScheduler().runTaskTimer(this, () -> {
      UpdateStats updateStats = new UpdateStats(this, commandHandler);
      updateStats.Update();
    }, 0L, 20L * 2);  // Run every 2 second (40 ticks)
  }

  private String buildStatusBar(int value, char fullChar, char emptyChar, boolean isWater){
    StringBuilder statusBar = new StringBuilder();
    int fullCount;
    if (value > 100) {
      value = 100;
    }
    if (isWater) {
      fullCount = (int) Math.ceil(value / 10.0);
    }
    else{
      fullCount = value/10;
    }
    int emptyCount = 10 - fullCount;
    if (isWater) {
      for (int i = 0; i < emptyCount; i++) {
        statusBar.append(emptyChar);
      }
      for (int i = 0; i < fullCount; i++) {
        statusBar.append(fullChar);
      }
    }
    else{
      for (int i = 0; i < fullCount; i++) {
        statusBar.append(fullChar);
      }
      for (int i = 0; i < emptyCount; i++) {
          statusBar.append(emptyChar);
      }
    }
      return statusBar.toString();
  }

  public void UpdateStatsBar() {
    char hydrationFull = '\uE043';
    char hydrationEmpty = '\uE044';
    char bladderFull = '\uE042';
    char bladderEmpty = '\uE045';
    char bowelsFull = '\uE048';
    char bowelsEmpty = '\uE049';

    Bukkit.getScheduler().runTaskTimer(this, () -> {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerStats stats = getPlayerStats(player.getUniqueId());
            if (stats != null && stats.getOptin() && stats.getUI() == 1) {
                ScoreBoard.updateSidebar(this, player, stats, UpdateStats.bladderfill.getOrDefault(player.getUniqueId(), 0.0), UpdateStats.bowelfill.getOrDefault(player.getUniqueId(), 0.0));
                if(player.getRemainingAir() == player.getMaximumAir()){
                  String hydrationBar = buildStatusBar((int)stats.getHydration(), hydrationFull, hydrationEmpty, true);
                  player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("\uF82B\uF82A\uF825"+hydrationBar));
                }
            }
            else if(stats != null && stats.getOptin() && stats.getUI() == 2){
              if(player.getRemainingAir() == player.getMaximumAir()){
                String hydrationBar = buildStatusBar((int)stats.getHydration(), hydrationFull, hydrationEmpty, true);
                String bladderBar = buildStatusBar((int)stats.getBladder(), bladderFull, bladderEmpty, false);
                String underwearImage = ScoreBoard.getUnderwearStatus((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness(), (int)stats.getUnderwearType(), stats.getUnderwearDesign(), 0);
                if (stats.getMessing()){
                  String bowelBar = buildStatusBar((int)stats.getBowels(), bowelsFull, bowelsEmpty, false);
                  String withBowels = "\uF82B\uF82A\uF825\uF829\uF828" + hydrationBar + "\uF82A\uF80C\uF829" + bladderBar + "\uF80B\uF809" + bowelBar + (stats.getshowunderwear() ? underwearImage : "");
                  player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(withBowels));
                }
                else{
                  String basicBar = "\uF82B\uF82A\uF825\uF829\uF828" + hydrationBar + "\uF82A\uF80C\uF829" + bladderBar + (stats.getshowunderwear() ? underwearImage : "");
                  player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(basicBar));
                }
              }
            }
            else {player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());}
        }
    }, 0L, 20L);  // Update every second (20 ticks)
  }

  public void CheckLittles(Player player, PlayerStats stats, Player target){
    if (stats != null) {
        // Check for target player argument and optional validation
        String image = "";
        String state = "";
        
        if (stats != null) {
            image = ScoreBoard.getUnderwearStatus((int)stats.getDiaperWetness(), (int)stats.getDiaperFullness(), (int)stats.getUnderwearType(), stats.getUnderwearDesign(), 2);
            String wetState = "";
            String fullnessState = "";
            
            int wetness = (int)stats.getDiaperWetness();
            int fullness = (int)stats.getDiaperFullness();
            
            if (wetness >= 100) {
                wetState = ChatColor.YELLOW + "Leaking";
            } else if (wetness > 50) {
                wetState = ChatColor.YELLOW + "Soaked";
            } else if (wetness > 1) {
                wetState = ChatColor.YELLOW + "Wet";
            }
            
            if (fullness >= 100) {
                fullnessState = ChatColor.GREEN + "Blowout";
            } else if (fullness > 50) {
                fullnessState = ChatColor.GREEN + "Full";
            } else if (fullness > 1) {
                fullnessState = ChatColor.GREEN + "Dirty";
            }
            
            state = wetState.isEmpty() ? fullnessState : 
                   fullnessState.isEmpty() ? wetState : 
                   wetState + ChatColor.WHITE + " And " + fullnessState;
            if (wetState.isEmpty() && fullnessState.isEmpty()) {
              state = "Clean";
            }
            player.sendTitle(image, state, 10, 20, 10);
            if (player != target) {
              target.sendTitle(ChatColor.GOLD + player.getName(), ChatColor.AQUA +" Just checked you.", 10, 20, 10);
            }
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.schedule(() -> rightclickCount.put(player.getUniqueId(), 0), 4, TimeUnit.SECONDS);
            // rightclickCount.put(player.getUniqueId(), 0);
        } 
    }
  }

  public void manageParticleEffects(Player player) {
    // UUID playerUUID = player.getUniqueId();
    // PlayerStats stats = getPlayerStats(playerUUID);
    
    // if (stats.getParticleEffects() == 2 && stats.getDiaperFullness() >= 100) {
    //   if (!ParticleEffects.containsKey(playerUUID)) {
    //     ParticleEffect effect = new ParticleEffect(player, this);
    //     ParticleEffects.put(playerUUID, effect.runTaskTimer(this, 0L, 5L)); // Run every second
    //   }
    // }
    // else if (stats.getParticleEffects() == 3 && stats.getDiaperWetness() >= 100 || stats.getDiaperFullness() >= 100){
    //   if (!ParticleEffects.containsKey(playerUUID)) {
    //     ParticleEffect effect = new ParticleEffect(player, this);
    //     ParticleEffects.put(playerUUID, effect.runTaskTimer(this, 0L, 5L)); // Run every second
    //   }
    // }
    // else if (stats.getParticleEffects() == 1 && stats.getDiaperWetness() >= 100) {
    //   if (!ParticleEffects.containsKey(playerUUID)) {
    //     ParticleEffect effect = new ParticleEffect(player, this);
    //     ParticleEffects.put(playerUUID, effect.runTaskTimer(this, 0L, 5L)); // Run every second
    //   }
    // }
    // else if (stats.getParticleEffects() == 0) {
    //     if (ParticleEffects.containsKey(playerUUID)) {
    //       ParticleEffects.get(playerUUID).cancel();
    //       ParticleEffects.remove(playerUUID);
    //     }
    // }
  }
  private ConcurrentHashMap<UUID, String> playerInputAwaiting = new ConcurrentHashMap<>();

  public boolean isAwaitingInput(UUID uuid) {
      return playerInputAwaiting.containsKey(uuid);
  }

  public String getAwaitingInputType(UUID uuid) {
      return playerInputAwaiting.get(uuid);
  }

  public void setAwaitingInput(UUID uuid, String type) {
      playerInputAwaiting.put(uuid, type);
  }

  public void clearAwaitingInput(UUID uuid) {
      playerInputAwaiting.remove(uuid);
  }
}
