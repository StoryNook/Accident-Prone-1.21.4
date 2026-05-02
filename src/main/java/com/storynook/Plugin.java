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
import com.storynook.Integrations.NannyVentureChatHook;
import com.storynook.Commands.NannyCommand;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Plugin extends JavaPlugin
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
  private List<String> hypnoWords;
  private FileConfiguration messagesConfig;
  public boolean VentureChat;
  public boolean PlaceholderAPI = false;
  public boolean citizensEnabled;
  private NannyManager nannyManager;
  private NannyCommand nannyCommand;
  private com.storynook.nanny.crypto.CryptoService cryptoService;
  private com.storynook.nanny.membership.OAuthHelper oauthHelper;

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
    DiaperPail pail = new DiaperPail();
    TickleAccidents tickle = new TickleAccidents(this);
    CustomFurnitureRemoval customFurnitureRemoval = new CustomFurnitureRemoval();
    cribs crib = new cribs(this);
    Laxative lax = new Laxative(this);
    BindingDiaper binding = new BindingDiaper(this);
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
          composite.add(new com.storynook.nanny.membership.PatreonMembershipProvider(
                  this, oauthHelper, cid, csec, redirectUri, tiers));
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

        // Load the Messing boolean
        boolean messingEnabled = config.getBoolean("Settings_Menu.Messing", false);
        globalConfig.put("Messing", messingEnabled);

        // Load the Caregiver boolean
        boolean CaregiverEnabled = config.getBoolean("Settings_Menu.Caregivers", false);
        globalConfig.put("Caregivers", CaregiverEnabled);

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
        globalConfig.put("Nanny_Chat_Min_Words", config.getInt("Nanny.Chat.Min_Words", 3));
        globalConfig.put("Nanny_Chat_Ambient_Chance", config.getInt("Nanny.Chat.Ambient_Chance", 1));
        globalConfig.put("Nanny_Chat_AI_Endpoint", config.getString("Nanny.Chat.AI.Endpoint", ""));

        getLogger().info("Successfully loaded global config.");

    } catch (Exception e) {
        getLogger().severe("Error loading config.yml: " + e.getMessage());
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
