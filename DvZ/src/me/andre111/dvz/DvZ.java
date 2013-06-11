package me.andre111.dvz;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import me.andre111.dvz.config.DVZFileConfiguration;
import me.andre111.dvz.dragon.DragonAttackListener;
import me.andre111.dvz.dragon.DragonAttackManager;
import me.andre111.dvz.dragon.DragonDeathListener;
import me.andre111.dvz.dwarf.DwarfManager;
import me.andre111.dvz.generator.DvZWorldProvider;
import me.andre111.dvz.item.ItemManager;
import me.andre111.dvz.listeners.Listener_Block;
import me.andre111.dvz.listeners.Listener_Entity;
import me.andre111.dvz.listeners.Listener_Game;
import me.andre111.dvz.listeners.Listener_Player;
import me.andre111.dvz.monster.MonsterManager;
import me.andre111.dvz.utils.FileHandler;
import me.andre111.dvz.utils.IconMenuHandler;
import me.andre111.dvz.utils.Invulnerability;
import me.andre111.dvz.utils.Item3DHandler;
import me.andre111.dvz.utils.ItemHandler;
import me.andre111.dvz.utils.Metrics;
import me.andre111.dvz.utils.Metrics.Graph;
import me.andre111.dvz.utils.MovementStopper;
import me.andre111.dvz.volatileCode.DynamicClassFunctions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import pgDev.bukkit.DisguiseCraft.DisguiseCraft;
import pgDev.bukkit.DisguiseCraft.api.DisguiseCraftAPI;
import pgDev.bukkit.DisguiseCraft.disguise.Disguise;

public class DvZ extends JavaPlugin {
	public static DvZ instance;
	//public Game game;
	private Game[] games = new Game[10];
	private GameDummy gameDummy = new GameDummy();
	public static int startedGames = 0;
	private Lobby lobby;
	public static DisguiseCraftAPI api;
	public static ProtocolManager protocolManager;
	
	public static DragonAttackManager dragonAtManager;
	public static DragonDeathListener dragonDeath;
	public static DragonAttackListener attackListener;
	public static MovementStopper moveStop;
	public static Invulnerability inVul;
	public static Item3DHandler item3DHandler;
	public static ItemStandManager itemStandManager;
	public static EffectManager effectManager;
	
	public static MonsterManager monsterManager;
	public static DwarfManager dwarfManager;
	public static ItemManager itemManager;
	
	public static Logger logger;
	public static String prefix = "[Dwarves vs Zombies] ";
	
	private static String lang = "en_EN";
	private static FileConfiguration configfile;
	private static FileConfiguration langfile;
	private static FileConfiguration enlangfile;
	private static FileConfiguration dragonsfile;
	private static FileConfiguration classfile;
	private static FileConfiguration monsterfile;
	private static FileConfiguration itemfile;
	private static FileConfiguration blockfile;
	public PluginDescriptionFile descriptionFile;
	
	private ArrayList<Integer> disabledCrafts = new ArrayList<Integer>();
	private ArrayList<Integer> disabledCraftsType2 = new ArrayList<Integer>();
	
	
	@Override
	public void onLoad() {
		logger = Logger.getLogger("Minecraft");

		// Get plugin description
		descriptionFile = this.getDescription();

		// Dynamic package detection
		if (!DynamicClassFunctions.setPackages()) {
			logger.log(Level.WARNING, "NMS/OBC package could not be detected, using " + DynamicClassFunctions.nmsPackage + " and " + DynamicClassFunctions.obcPackage);
		}
		DynamicClassFunctions.setClasses();
		DynamicClassFunctions.setMethods();
		DynamicClassFunctions.setFields();
	}
	
	@Override
	public void onEnable() {
		DvZ.instance = this;
		
		initConfig();
		
		//Disguisecraft check
		if (this.getConfig().getString("disable_dcraft_check", "false")!="true") {
			if (!Bukkit.getPluginManager().isPluginEnabled("DisguiseCraft"))
			{
				Bukkit.getServer().getConsoleSender().sendMessage(prefix+" "+ChatColor.RED+"DisguiseCraft could not be found, disabling...");
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
			if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib"))
			{
				Bukkit.getServer().getConsoleSender().sendMessage(prefix+" "+ChatColor.RED+"ProtocolLib could not be found, disabling...");
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
		}
		DvZ.api = DisguiseCraft.getAPI();
		DvZ.protocolManager = ProtocolLibrary.getProtocolManager();
		
		Spellcontroller.plugin = this;
		Classswitcher.plugin = this;
		ItemHandler.plugin = this;
		IconMenuHandler.instance = new IconMenuHandler(this);
		
		itemManager = new ItemManager();
		itemManager.loadItems();
		dwarfManager = new DwarfManager();
		dwarfManager.loadDwarfes();
		monsterManager = new MonsterManager();
		monsterManager.loadMonsters();
		
		dragonAtManager = new DragonAttackManager();
		dragonAtManager.loadAttacks();
		dragonDeath = new DragonDeathListener(this);
		attackListener = new DragonAttackListener(this);
		moveStop = new MovementStopper(this);
		inVul = new Invulnerability(this);
		item3DHandler = new Item3DHandler(this);
		itemStandManager = new ItemStandManager();
		effectManager = new EffectManager();
		effectManager.loadEffects();
		
		BlockManager.loadConfig();
		
		try {
		    Metrics metrics = new Metrics(this);
		    
		    // Plot the total amount of protections
		    Graph graph = metrics.createGraph("Running Games");
		    
		    graph.addPlotter(new Metrics.Plotter("Running Games") {

		    	@Override
		    	public int getValue() {
		    		DvZ.startedGames = 0;
		    		return DvZ.startedGames;
		    	}

		    });
		    
		    metrics.start();
		    metrics.enable();
		} catch (IOException e) {
		    // Failed to submit the stats :-(
		}
		
		if(getConfig().getString("use_lobby", "true").equals("true"))
			Bukkit.getServer().createWorld(new WorldCreator(this.getConfig().getString("world_prefix", "DvZ_")+"Lobby"));
		File f = new File(Bukkit.getServer().getWorldContainer().getPath()+"/"+this.getConfig().getString("world_prefix", "DvZ_")+"Worlds/");
		if(!f.exists()) f.mkdirs();
		f = new File(Bukkit.getServer().getWorldContainer().getPath()+"/"+this.getConfig().getString("world_prefix", "DvZ_")+"Worlds/Type1/");
		if(!f.exists()) f.mkdirs();
		f = new File(Bukkit.getServer().getWorldContainer().getPath()+"/"+this.getConfig().getString("world_prefix", "DvZ_")+"Worlds/Type2/");
		if(!f.exists()) f.mkdirs();
		for (int i=0; i<10; i++) {
			File f2 = new File(Bukkit.getServer().getWorldContainer().getPath()+"/"+this.getConfig().getString("world_prefix", "DvZ_")+"Main"+i+"/");
			if(f2.exists()) FileHandler.deleteFolder(f2);
		}
		//w_main = Bukkit.getServer().createWorld(new WorldCreator(this.getConfig().getString("world_prefix", "DvZ_")+"Main"));
		
		lobby = new Lobby(this);
		
		new Listener_Player(this);
		new Listener_Block(this);
		new Listener_Entity(this);
		new Listener_Game(this);
		new DvZWorldProvider(this);
		
		//init and reset games
		for(int i=0; i<games.length; i++) {
			int type = getConfig().getInt("game"+i, 1);
			if(type>=1 && type<=2) {
				games[i] = new Game(this, type);
				games[i].reset(false);
			}
		}
		//
		
		CommandExecutorDvZ command = new CommandExecutorDvZ(this);
		for(String st : getDescription().getCommands().keySet()) {
			getCommand(st).setExecutor(command);
		}
		
		//Run Main Game Managment
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				for(int i=0; i<games.length; i++) {
					if (games[i]!=null) {
						games[i].tick();
					}
				}
		    }
		}, 20, 20);
		//
	}
	
	@Override
	public void onDisable() {
		//remove all zombies
		item3DHandler.removeAll();
		
		//remove all release inventories
		for(int i=0; i<games.length; i++) {
			if (games[i]!=null) {
				games[i].reset(false);
			}
		}
	}
	
	public static void log(String s) {
		logger.info(prefix+s);
	}
	
	private void initConfig() {
		exportConfigs();
		
		if (!new File(getDataFolder(), "config.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/config.yml"), new File(getDataFolder(), "config.yml"));
			} catch (IOException e) {}
			log("Generating default config.");
			//saveDefaultConfig();
		}
		//Classes and stuff
		if (!new File(getDataFolder(), "dragons.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/dragons.yml"), new File(getDataFolder(), "dragons.yml"));
			} catch (IOException e) {}
		}
		if (!new File(getDataFolder(), "classes.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/classes.yml"), new File(getDataFolder(), "classes.yml"));
			} catch (IOException e) {}
		}
		if (!new File(getDataFolder(), "monster.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/monster.yml"), new File(getDataFolder(), "monster.yml"));
			} catch (IOException e) {}
		}
		if (!new File(getDataFolder(), "items.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/items.yml"), new File(getDataFolder(), "items.yml"));
			} catch (IOException e) {}
		}
		if (!new File(getDataFolder(), "blocks.yml").exists()) {
			try {
				FileHandler.copyFolder(new File(getDataFolder(), "config/default/blocks.yml"), new File(getDataFolder(), "blocks.yml"));
			} catch (IOException e) {}
		}
		DvZ.lang = this.getConfig().getString("language", "en_EN");
		DvZ.langfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "lang/lang_"+lang+".yml"));
		DvZ.enlangfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "lang/lang_en_EN.yml"));
		DvZ.configfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "config.yml"));
		DvZ.dragonsfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "dragons.yml"));
		DvZ.classfile =  DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "classes.yml"));
		DvZ.monsterfile =  DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "monster.yml"));
		DvZ.itemfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "items.yml"));
		DvZ.blockfile = DVZFileConfiguration.loadConfiguration(new File(this.getDataFolder(), "blocks.yml"));
	
		loadConfigs();
	}
	
	private void exportConfigs() {
		saveResource("config/default/config.yml", true);
		saveResource("config/default/dragons.yml", true);
		saveResource("config/default/classes.yml", true);
		saveResource("config/default/monster.yml", true);
		saveResource("config/default/items.yml", true);
		saveResource("config/default/blocks.yml", true);
		//language
		for(String st : Language.getPossibleLanguages()) {
			if(getResource("lang/lang_"+st+".yml")!=null)
				saveResource("lang/lang_"+st+".yml", true);
			if(getResource("lang/unfinished/lang_"+st+".yml")!=null)
				saveResource("lang/unfinished/lang_"+st+".yml", true);
		}
	}
	
	private void loadConfigs() {
		//disabled crafting recipies
		//-----------------------------
		for(int id : DvZ.configfile.getIntegerList("disables_crafts")) {
			disabledCrafts.add(id);
		}
		//type 2(new dvz needs more crafts to disable)
		for(int id : DvZ.configfile.getIntegerList("disables_crafts_type2")) {
			disabledCraftsType2.add(id);
		}
	}
	
	public static FileConfiguration getLanguage() {
		return langfile;
	}
	public static FileConfiguration getDefaultLanguage() {
		return enlangfile;
	}
	public static FileConfiguration getDragonsFile() {
		return dragonsfile;
	}
	public static FileConfiguration getClassFile() {
		return classfile;
	}
	public static FileConfiguration getMonsterFile() {
		return monsterfile;
	}
	public static FileConfiguration getItemFile() {
		return itemfile;
	}
	public static FileConfiguration getBlockFile() {
		return blockfile;
	}
	public static FileConfiguration getStaticConfig() {
		return configfile;
	}
	
	//TODO - remove temporary workaround
	@SuppressWarnings("deprecation")
	public static void updateInventory(Player player) {
		player.updateInventory();
	}
	
	//#######################################
	//Bekomme Spiel in dem das der Spieler ist
	//#######################################
	public Game getPlayerGame(String player) {
		for(int i=0; i<games.length; i++) {
			if (games[i]!=null) {
				if (games[i].isPlayer(player))
				{
					return games[i];
				}
			}
		}
		
		return null;
	}
	
	public int getGameID(Game game) {
		for(int i=0; i<games.length; i++) {
			if (games[i]==game) {
				return i;
			}
		}
		
		return -1;
	}
	
	public Game getGame(int id) {
		return games[id];
	}

	public GameDummy getDummy() {
		return gameDummy;
	}
	
	public Lobby getLobby() {
		return lobby;
	}
	
	public int getRunningGameCount() {
		int count = 0;
		
		for(int i=0; i<games.length; i++) {
			if(games[i]!=null)
			if(games[i].isRunning()) {
				count++;
			}
		}
		
		return count;
	}
	
    public void joinGame(Player player, Game game) {
    	joinGame(player, game, false);
	}
	
	public void joinGame(Player player, Game game, boolean autojoin) {
		game.setPlayerState(player.getName(), 1);
		if(DvZ.getStaticConfig().getString("use_lobby", "true").equals("true"))
			player.teleport(Bukkit.getServer().getWorld(getConfig().getString("world_prefix", "DvZ_")+"Lobby").getSpawnLocation());
		ItemHandler.clearInv(player);

		player.sendMessage(getLanguage().getString("string_self_added","You have been added to the game!"));

		//autoadd player
		if(game.getState()>1) {
			if (getConfig().getString("autoadd_players","false")=="true" || autojoin) {
				if(!game.released) {
					game.setPlayerState(player.getName(), 2);
					player.sendMessage(getLanguage().getString("string_choose","Choose your class!"));
					game.addDwarfItems(player);

					game.broadcastMessage(getLanguage().getString("string_autoadd","Autoadded -0- as a Dwarf to the Game!").replace("-0-", player.getDisplayName()));
				} else {
					game.setPlayerState(player.getName(), 3);
					player.sendMessage(getLanguage().getString("string_choose","Choose your class!"));
					game.addMonsterItems(player);

					game.broadcastMessage(getLanguage().getString("string_autoadd_m","Autoadded -0- as a Monster to the Game!").replace("-0-", player.getDisplayName()));
				}
			}
		}
	}
	
	public static void disguiseP(Player player, Disguise disguise) {
		if(api.isDisguised(player)) {
			api.changePlayerDisguise(player, disguise);
		} else {
			api.disguisePlayer(player, disguise);
		}
	}
	
	public boolean isCraftDisabled(int id, int gameType) {
		if (gameType==1)
			return disabledCrafts.contains(id);
		else if (gameType==2)
			return disabledCraftsType2.contains(id);
		else
			return false;
	}
	
	public static boolean isPathable(Block block) {
        return isPathable(block.getType());
	}
	public static boolean isPathable(Material material) {
		return
				material == Material.AIR ||
				material == Material.SAPLING ||
				material == Material.WATER ||
				material == Material.STATIONARY_WATER ||
				material == Material.POWERED_RAIL ||
				material == Material.DETECTOR_RAIL ||
				material == Material.LONG_GRASS ||
				material == Material.DEAD_BUSH ||
				material == Material.YELLOW_FLOWER ||
				material == Material.RED_ROSE ||
				material == Material.BROWN_MUSHROOM ||
				material == Material.RED_MUSHROOM ||
				material == Material.TORCH ||
				material == Material.FIRE ||
				material == Material.REDSTONE_WIRE ||
				material == Material.CROPS ||
				material == Material.SIGN_POST ||
				material == Material.LADDER ||
				material == Material.RAILS ||
				material == Material.WALL_SIGN ||
				material == Material.LEVER ||
				material == Material.STONE_PLATE ||
				material == Material.WOOD_PLATE ||
				material == Material.REDSTONE_TORCH_OFF ||
				material == Material.REDSTONE_TORCH_ON ||
				material == Material.STONE_BUTTON ||
				material == Material.SNOW ||
				material == Material.SUGAR_CANE_BLOCK ||
				material == Material.VINE ||
				material == Material.WATER_LILY ||
				material == Material.NETHER_STALK ||
				material == Material.TRIPWIRE_HOOK ||
				material == Material.TRIPWIRE ||
				material == Material.POTATO ||
				material == Material.CARROT ||
				material == Material.WOOD_BUTTON;
	}
	public final static HashSet<Byte> transparent = new HashSet<Byte>();
	static {
		transparent.add((byte)0);
		transparent.add((byte)6);
		transparent.add((byte)8);
		transparent.add((byte)9);
		transparent.add((byte)27);
		transparent.add((byte)28);
		transparent.add((byte)31);
		transparent.add((byte)32);
		transparent.add((byte)37);
		transparent.add((byte)38);
		transparent.add((byte)39);
		transparent.add((byte)40);
		transparent.add((byte)50);
		transparent.add((byte)51);
		transparent.add((byte)55);
		transparent.add((byte)59);
		transparent.add((byte)63);
		transparent.add((byte)65);
		transparent.add((byte)66);
		transparent.add((byte)68);
		transparent.add((byte)69);
		transparent.add((byte)70);
		transparent.add((byte)71);
		transparent.add((byte)75);
		transparent.add((byte)76);
		transparent.add((byte)77);
		transparent.add((byte)78);
		transparent.add((byte)83);
		transparent.add((byte)106);
		transparent.add((byte)111);
		transparent.add((byte)115);
		transparent.add((byte)131);
		transparent.add((byte)132);
		transparent.add((byte)141);
		transparent.add((byte)142);
		transparent.add((byte)143);
	}
	
	public static int scheduleRepeatingTask(final Runnable task, int delay, int interval) {
		return Bukkit.getScheduler().scheduleSyncRepeatingTask(DvZ.instance, task, delay, interval);
	}

	public static void cancelTask(int taskId) {
		Bukkit.getScheduler().cancelTask(taskId);
	}
}
