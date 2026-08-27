package cz.tvujnick.reporting;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Reporting extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static Reporting instance;
    private Map<Integer, Report> reports = new HashMap<>();
    private File reportsFile;
    private FileConfiguration reportsConfig;
    private int nextId = 1;

    public static Reporting getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        getCommand("report").setExecutor(this);
        getCommand("report").setTabCompleter(this);
        getCommand("reports").setExecutor(this);
        getCommand("reports").setTabCompleter(this);

        setupReportsFile();
        loadReports();
        startAutoSave();

        getLogger().info("Plugin Reporting uspesne zapnut!");
    }

    @Override
    public void onDisable() {
        saveReportsToDisk();
    }

    private void setupReportsFile() {
        reportsFile = new File(getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            try {
                reportsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
    }

    private void loadReports() {
        reports.clear();
        if (reportsConfig.contains("reports")) {
            for (String key : reportsConfig.getConfigurationSection("reports").getKeys(false)) {
                int id = Integer.parseInt(key);
                String path = "reports." + id + ".";
                Report r = new Report(
                        id,
                        reportsConfig.getString(path + "reporter"),
                        reportsConfig.getString(path + "reported"),
                        reportsConfig.getString(path + "reason"),
                        reportsConfig.getString(path + "time"),
                        reportsConfig.getBoolean(path + "open")
                );
                reports.put(id, r);
                if (id >= nextId) nextId = id + 1;
            }
        }
    }

    private void saveReportsToDisk() {
        for (Report r : reports.values()) {
            String path = "reports." + r.id + ".";
            reportsConfig.set(path + "reporter", r.reporter);
            reportsConfig.set(path + "reported", r.reported);
            reportsConfig.set(path + "reason", r.reason);
            reportsConfig.set(path + "time", r.time);
            reportsConfig.set(path + "open", r.open);
        }
        try {
            reportsConfig.save(reportsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAutoSave() {
        int intervalMin = getConfig().getInt("settings.auto-save-interval", 10);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveReportsToDisk, 20L * 60 * intervalMin, 20L * 60 * intervalMin);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        // ===================== /REPORT =====================
        if (command.getName().equalsIgnoreCase("report")) {
            if (args.length < 2) {
                sender.sendMessage(color(getConfig().getString("messages.report-help")));
                return true;
            }

            String reporter = sender.getName();
            String reported = args[0];

            if (reporter.equalsIgnoreCase(reported)) {
                sender.sendMessage(color(getConfig().getString("messages.cannot-report-self")));
                return true;
            }

            String reason = String.join(" ", args).substring(reported.length() + 1);
            String time = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
            int id = nextId++;

            Report newReport = new Report(id, reporter, reported, reason, time, true);
            reports.put(id, newReport);

            sender.sendMessage(color(getConfig().getString("messages.report-success")
                    .replace("%reported%", reported).replace("%id%", String.valueOf(id))));

            String notifyMsg = color(getConfig().getString("messages.notify-staff")
                    .replace("%reporter%", reporter).replace("%reported%", reported)
                    .replace("%reason%", reason).replace("%id%", String.valueOf(id)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("reporting.informate")) p.sendMessage(notifyMsg);
            }

            String hook = getConfig().getString("discord.webhook-url");
            String title = getConfig().getString("discord.new-report-embed.title").replace("%id%", String.valueOf(id));
            String desc = getConfig().getString("discord.new-report-embed.description")
                    .replace("%reporter%", reporter).replace("%reported%", reported).replace("%reason%", reason);
            int color = getConfig().getInt("discord.new-report-embed.color");
            DiscordWebhook.sendEmbed(hook, title, desc, color);

            return true;
        }

        // ===================== /REPORTS =====================
        if (command.getName().equalsIgnoreCase("reports")) {
            if (!sender.hasPermission("reporting.admin")) {
                sender.sendMessage(color(getConfig().getString("messages.no-permission")));
                return true;
            }

            // /reports reload (Vyžaduje reporting.admin.admin)
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("reporting.admin.admin")) {
                    sender.sendMessage(color(getConfig().getString("messages.no-permission")));
                    return true;
                }
                reloadConfig();
                setupReportsFile();
                loadReports();
                sender.sendMessage(color(getConfig().getString("messages.reload-success")));
                return true;
            }

            // /reports savedata (Vyžaduje reporting.admin.admin)
            if (args.length == 1 && args[0].equalsIgnoreCase("savedata")) {
                if (!sender.hasPermission("reporting.admin.admin")) {
                    sender.sendMessage(color(getConfig().getString("messages.no-permission")));
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    saveReportsToDisk();
                    sender.sendMessage(color(getConfig().getString("messages.savedata-success")));
                });
                return true;
            }

            // /reports <ID> close
            if (args.length == 2 && args[1].equalsIgnoreCase("close")) {
                try {
                    int id = Integer.parseInt(args[0]);
                    if (reports.containsKey(id) && reports.get(id).open) {
                        reports.get(id).open = false;
                        sender.sendMessage(color(getConfig().getString("messages.report-closed-chat").replace("%id%", String.valueOf(id))));

                        String hook = getConfig().getString("discord.webhook-url");
                        String title = getConfig().getString("discord.closed-report-embed.title").replace("%id%", String.valueOf(id));
                        String desc = getConfig().getString("discord.closed-report-embed.description");
                        int color = getConfig().getInt("discord.closed-report-embed.color");
                        DiscordWebhook.sendEmbed(hook, title, desc, color);
                    } else {
                        sender.sendMessage(color(getConfig().getString("messages.report-not-found")));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(color(getConfig().getString("messages.report-not-found")));
                }
                return true;
            }

            // Výpis reportů (/reports)
            sender.sendMessage(color(getConfig().getString("messages.reports-list-header")));
            boolean found = false;
            String format = getConfig().getString("messages.reports-list-format");

            for (Report r : reports.values()) {
                if (r.open) {
                    found = true;
                    sender.sendMessage(color(format
                            .replace("%id%", String.valueOf(r.id))
                            .replace("%time%", r.time)
                            .replace("%reporter%", r.reporter)
                            .replace("%reported%", r.reported)
                            .replace("%reason%", r.reason)));
                }
            }

            if (!found) {
                sender.sendMessage(color(getConfig().getString("messages.reports-list-empty")));
            }
            return true;
        }

        return false;
    }

    // ===================== REGISTRACE SUBCOMMANDŮ A NAŠEPTÁVÁNÍ (TAB) =====================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("report")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getName().equalsIgnoreCase(sender.getName())) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        if (command.getName().equalsIgnoreCase("reports") && sender.hasPermission("reporting.admin")) {
            if (args.length == 1) {
                if (sender.hasPermission("reporting.admin.admin")) {
                    completions.add("reload");
                    completions.add("savedata");
                }
                for (Report r : reports.values()) {
                    if (r.open) completions.add(String.valueOf(r.id));
                }
            } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("savedata")) {
                completions.add("close");
            }
        }

        return completions;
    }
}
