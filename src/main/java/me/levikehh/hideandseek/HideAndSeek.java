package me.levikehh.hideandseek;

import org.bukkit.plugin.java.JavaPlugin;
import hu.nomindz.devkit.managers.DatabaseManager;
import hu.nomindz.devkit.managers.TimerManager;
import me.levikehh.hideandseek.commands.HideAndSeekCommands;
import me.levikehh.hideandseek.config.HideAndSeekConfig;
import me.levikehh.hideandseek.listeners.MechanicsListener;
import me.levikehh.hideandseek.listeners.PlayerDisconnectListener;
import me.levikehh.hideandseek.managers.GameManager;
import me.levikehh.hideandseek.managers.LobbyManager;
import me.levikehh.hideandseek.managers.MatchManager;
import me.levikehh.hideandseek.managers.MechanicsManager;
import me.levikehh.hideandseek.managers.TeamManager;
import hu.nomindz.devkit.command.CommandRegistry;
import hu.nomindz.devkit.config.ConfigFactory;
import hu.nomindz.devkit.config.ConfigManager;

public class HideAndSeek extends JavaPlugin {
    private DatabaseManager databaseManager;
    private TimerManager timer;
    private ConfigManager<HideAndSeekConfig> configManager;
    private CommandRegistry commands;

    private TeamManager teamManager;
    private MatchManager matchManager;
    private LobbyManager lobbyManager;
    private GameManager gameManager;
    private MechanicsManager mechanicsManager;

    @Override
    public void onEnable() {
        // Initialize baseline requirement
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create data folder: " + getDataFolder());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = ConfigFactory.create(
                this,
                HideAndSeekConfig.class,
                "config.yml",
                java.util.List.of("devkit-config.yml",
                        "config.yml"),
                "config_version",
                1,
                null);
        this.configManager.loadOrCreate();

        this.databaseManager = DatabaseManager.getInstance(this, () -> configManager.get().database());
        this.databaseManager.initializeFromResource("schema.sql");

        this.timer = TimerManager.getInstance(this);

        // Plugin dependent
        this.teamManager = TeamManager.getInstance(this);
        this.matchManager = MatchManager.getInstance(this);
        this.lobbyManager = LobbyManager.getInstance(this);
        this.gameManager = GameManager.getInstance(this);
        this.mechanicsManager = MechanicsManager.getInstance(
            this,
            (player) -> this.teamManager.isHider(this.gameManager.getMatch(player), player),
            (player) -> this.teamManager.isSeeker(this.gameManager.getMatch(player), player));

        // Register commands
        this.registerCommands();
        // Register listeners
        this.registerListeners();

        getLogger().info("HideAndSeek plugin enabled");
    }

    @Override
    public void onDisable() {
        if (this.timer != null) {
            this.timer.stopAll();
        }

        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        getLogger().info("HideAndSeek plugin disabled");
    }

    private void registerCommands() {
        this.commands = new CommandRegistry(this);

        this.commands.registerAll(new HideAndSeekCommands());

        getLogger().info("Commands registered.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MechanicsListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDisconnectListener(this), this);

        getLogger().info("Listeners registered.");
    }

    public HideAndSeekConfig config() {
        return this.configManager.get();
    }

    public TimerManager getTimer() {
        return this.timer;
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public MatchManager getMatchManager() {
        return this.matchManager;
    }

    public GameManager getGameManager() {
        return this.gameManager;
    }

    public LobbyManager getLobbyManager() {
        return this.lobbyManager;
    }

    public TeamManager getTeamManager() {
        return this.teamManager;
    }

    public MechanicsManager getMechanicsManager() {
        return this.mechanicsManager;
    }
}
