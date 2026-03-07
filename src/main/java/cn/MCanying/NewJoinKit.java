package cn.MCanying;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class NewJoinKit extends JavaPlugin implements Listener, TabCompleter {

    private FileConfiguration config;
    private File configFile;
    private final List<UUID> receivedKitUUIDs = new ArrayList<>();

    // 彻底移除RESET：用白色替代（视觉效果一致），避免API版本冲突
    private final Component PREFIX = Component.text("[NewJoinKit] ", NamedTextColor.GRAY)
            .append(Component.text(" ", NamedTextColor.WHITE));

    // 遗留格式序列化器（兼容&符号颜色码）
    private final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        createConfig();
        loadReceivedData();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        logStartupInfo();
    }

    @Override
    public void onDisable() {
        saveReceivedData();
        logShutdownInfo();
    }

    private void createConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                setDefaultConfig();
                config.save(configFile);
                getLogger().info("默认配置文件已创建");
            } catch (IOException e) {
                getLogger().severe("创建配置文件失败: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void setDefaultConfig() {
        // 基础设置
        config.addDefault("settings.only-first-join", true);
        config.addDefault("settings.give-delay-ticks", 20);
        config.addDefault("settings.clear-inventory", false);
        config.addDefault("settings.announce-to-all", true);
        config.addDefault("settings.announce-message", "&e欢迎新玩家 &a%player% &e加入服务器！已发放新手礼包！");
        config.addDefault("settings.private-message", "&a您已获得新手礼包！请查收背包！");

        // 物品配置
        config.addDefault("items.sword.material", "DIAMOND_SWORD");
        config.addDefault("items.sword.amount", 1);
        config.addDefault("items.sword.name", "&b新手剑");
        config.addDefault("items.sword.lore", Arrays.asList("&7这是给新手的礼物", "&7祝游戏愉快！"));
        config.addDefault("items.sword.slot", 0);

        config.addDefault("items.food.material", "BREAD");
        config.addDefault("items.food.amount", 16);
        config.addDefault("items.food.name", "&e新手食物");
        config.addDefault("items.food.lore", Arrays.asList("&7别饿着肚子冒险"));
        config.addDefault("items.food.slot", 1);

        config.addDefault("items.pickaxe.material", "IRON_PICKAXE");
        config.addDefault("items.pickaxe.amount", 1);
        config.addDefault("items.pickaxe.name", "&7新手镐子");
        config.addDefault("items.pickaxe.lore", Arrays.asList("&7开始你的挖矿之旅"));
        config.addDefault("items.pickaxe.slot", 2);

        config.options().copyDefaults(true);
    }

    public void reloadPluginConfig() {
        if (configFile != null) {
            config = YamlConfiguration.loadConfiguration(configFile);
            getLogger().info("配置文件已重新加载");
        }
        loadReceivedData();
    }

    private void loadReceivedData() {
        File dataFile = new File(getDataFolder(), "data.yml");
        if (dataFile.exists()) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            receivedKitUUIDs.clear();

            List<String> uuidStrings = dataConfig.getStringList("received-players");
            for (String uuidStr : uuidStrings) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    receivedKitUUIDs.add(uuid);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("无效的UUID: " + uuidStr);
                }
            }
            getLogger().info("加载了 " + receivedKitUUIDs.size() + " 条领取记录");
        }
    }

    private void saveReceivedData() {
        File dataFile = new File(getDataFolder(), "data.yml");
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        List<String> uuidStrings = new ArrayList<>();
        for (UUID uuid : receivedKitUUIDs) {
            uuidStrings.add(uuid.toString());
        }

        dataConfig.set("received-players", uuidStrings);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("保存数据失败: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (config.getBoolean("settings.only-first-join", true) && receivedKitUUIDs.contains(playerUUID)) {
            return;
        }

        int delay = config.getInt("settings.give-delay-ticks", 20);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;

            giveKit(player);
            if (!receivedKitUUIDs.contains(playerUUID)) {
                receivedKitUUIDs.add(playerUUID);
                saveReceivedData();
            }

            sendMessages(player);
        }, delay);
    }

    private void giveKit(Player player) {
        if (config.getBoolean("settings.clear-inventory", false)) {
            player.getInventory().clear();
        }

        if (!config.contains("items")) {
            getLogger().warning("配置中无items节点");
            return;
        }

        Set<String> itemKeys = config.getConfigurationSection("items").getKeys(false);
        for (String key : itemKeys) {
            giveSingleItem(player, "items." + key);
        }
    }

    private void giveSingleItem(Player player, String path) {
        try {
            String materialName = config.getString(path + ".material");
            if (materialName == null) return;

            Material material = Material.valueOf(materialName.toUpperCase());
            int amount = Math.max(1, config.getInt(path + ".amount", 1));
            int slot = config.getInt(path + ".slot", -1);

            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                // 替换过时的setDisplayName - 使用Component
                String nameStr = config.getString(path + ".name");
                if (nameStr != null) {
                    Component nameComponent = LEGACY_SERIALIZER.deserialize(nameStr);
                    meta.displayName(nameComponent);
                }

                // 替换过时的setLore - 使用Component列表
                List<String> loreStrings = config.getStringList(path + ".lore");
                if (!loreStrings.isEmpty()) {
                    List<Component> loreComponents = new ArrayList<>();
                    for (String loreStr : loreStrings) {
                        loreComponents.add(LEGACY_SERIALIZER.deserialize(loreStr));
                    }
                    meta.lore(loreComponents);
                }

                item.setItemMeta(meta);
            }

            if (slot >= 0 && slot < player.getInventory().getSize()) {
                player.getInventory().setItem(slot, item);
            } else {
                player.getInventory().addItem(item);
            }

        } catch (Exception e) {
            getLogger().warning("发放物品失败 " + path + ": " + e.getMessage());
        }
    }

    private void sendMessages(Player player) {
        // 替换过时的broadcastMessage - 使用Adventure的broadcast
        if (config.getBoolean("settings.announce-to-all", true)) {
            String announceStr = config.getString("settings.announce-message", "")
                    .replace("%player%", player.getName());
            if (!announceStr.isEmpty()) {
                Component announceComponent = PREFIX.append(LEGACY_SERIALIZER.deserialize(announceStr));
                Bukkit.getServer().sendMessage(announceComponent);
            }
        }

        // 私人消息 - 使用Adventure的sendMessage
        String privateMsgStr = config.getString("settings.private-message", "");
        if (!privateMsgStr.isEmpty()) {
            Component privateComponent = PREFIX.append(LEGACY_SERIALIZER.deserialize(privateMsgStr));
            player.sendMessage(privateComponent);
        }
    }

    private void registerCommands() {
        if (getCommand("newjoinkit") != null) {
            getCommand("newjoinkit").setExecutor(this);
            getCommand("newjoinkit").setTabCompleter(this);
        } else {
            getLogger().severe("命令注册失败，请检查plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("newjoinkit")) return false;

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
            case "?":
                sendHelp(sender);
                break;
            case "info":
                sendInfo(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "test":
                handleTest(sender);
                break;
            default:
                sender.sendMessage(PREFIX.append(Component.text("未知命令！使用 /newjoinkit help 查看帮助", NamedTextColor.RED)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("info");
            if (sender.hasPermission("newjoinkit.admin")) {
                completions.add("reload");
                completions.add("give");
                completions.add("reset");
                completions.add("list");
                completions.add("test");
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("give") || subCmd.equals("reset")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        // 过滤匹配项
        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
        return completions;
    }

    // 命令处理方法
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("-------- NewJoinKit 帮助 --------", NamedTextColor.GRAY)
                .decorate(TextDecoration.STRIKETHROUGH));
        sender.sendMessage(Component.text("/newjoinkit help ", NamedTextColor.GRAY)
                .append(Component.text("- 显示此帮助", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/newjoinkit info ", NamedTextColor.GRAY)
                .append(Component.text("- 显示插件信息", NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("[管理员命令]", NamedTextColor.RED));
        sender.sendMessage(Component.text("/newjoinkit reload ", NamedTextColor.GRAY)
                .append(Component.text("- 重新加载配置", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/newjoinkit give <玩家名> ", NamedTextColor.GRAY)
                .append(Component.text("- 发放礼包", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/newjoinkit reset <玩家名> ", NamedTextColor.GRAY)
                .append(Component.text("- 重置领取记录", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/newjoinkit list ", NamedTextColor.GRAY)
                .append(Component.text("- 查看领取列表", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/newjoinkit test ", NamedTextColor.GRAY)
                .append(Component.text("- 测试礼包（给自己）", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY)
                .decorate(TextDecoration.STRIKETHROUGH));
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("-------- NewJoinKit 信息 --------", NamedTextColor.GRAY)
                .decorate(TextDecoration.STRIKETHROUGH));
        sender.sendMessage(Component.text("插件版本: ", NamedTextColor.GRAY)
                .append(Component.text("1.0.0", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("作者: ", NamedTextColor.GRAY)
                .append(Component.text("MCanying", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("适配版本: ", NamedTextColor.GRAY)
                .append(Component.text("1.20.1 (Java 17)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("已领取玩家: ", NamedTextColor.GRAY)
                .append(Component.text(receivedKitUUIDs.size() + " 人", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY)
                .decorate(TextDecoration.STRIKETHROUGH));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("newjoinkit.admin")) {
            sender.sendMessage(PREFIX.append(Component.text("无权限！", NamedTextColor.RED)));
            return;
        }
        reloadPluginConfig();
        sender.sendMessage(PREFIX.append(Component.text("配置已重新加载！", NamedTextColor.GREEN)));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newjoinkit.admin")) {
            sender.sendMessage(PREFIX.append(Component.text("无权限！", NamedTextColor.RED)));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text("用法: /newjoinkit give <玩家名>", NamedTextColor.RED)));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text("玩家不在线！", NamedTextColor.RED)));
            return;
        }

        giveKit(target);
        sender.sendMessage(PREFIX.append(Component.text("已给 ").color(NamedTextColor.GREEN)
                .append(Component.text(target.getName()).color(NamedTextColor.WHITE))
                .append(Component.text(" 发放礼包！").color(NamedTextColor.GREEN))));
        target.sendMessage(PREFIX.append(Component.text("管理员给您发放了新手礼包！", NamedTextColor.GREEN)));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newjoinkit.admin")) {
            sender.sendMessage(PREFIX.append(Component.text("无权限！", NamedTextColor.RED)));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text("用法: /newjoinkit reset <玩家名>", NamedTextColor.RED)));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayer(args[1]);

        UUID targetUUID = target.getUniqueId();
        if (receivedKitUUIDs.remove(targetUUID)) {
            saveReceivedData();
            sender.sendMessage(PREFIX.append(Component.text("已重置 ").color(NamedTextColor.GREEN)
                    .append(Component.text(args[1]).color(NamedTextColor.WHITE))
                    .append(Component.text(" 的领取记录！").color(NamedTextColor.GREEN))));
        } else {
            sender.sendMessage(PREFIX.append(Component.text("该玩家未领取过礼包！", NamedTextColor.RED)));
        }
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("newjoinkit.admin")) {
            sender.sendMessage(PREFIX.append(Component.text("无权限！", NamedTextColor.RED)));
            return;
        }

        sender.sendMessage(PREFIX.append(Component.text("已领取礼包的玩家 (").color(NamedTextColor.YELLOW)
                .append(Component.text(receivedKitUUIDs.size()).color(NamedTextColor.GREEN))
                .append(Component.text("):").color(NamedTextColor.YELLOW))));

        int count = 0;
        for (UUID uuid : receivedKitUUIDs) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            sender.sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                    .append(Component.text(name).color(NamedTextColor.WHITE)));

            if (++count >= 10) {
                sender.sendMessage(Component.text("... 还有 " + (receivedKitUUIDs.size() - 10) + " 人", NamedTextColor.GRAY));
                break;
            }
        }

        if (receivedKitUUIDs.isEmpty()) {
            sender.sendMessage(Component.text("- 暂无玩家领取", NamedTextColor.GRAY));
        }
    }

    private void handleTest(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX.append(Component.text("只有玩家可使用！", NamedTextColor.RED)));
            return;
        }
        if (!sender.hasPermission("newjoinkit.admin")) {
            sender.sendMessage(PREFIX.append(Component.text("无权限！", NamedTextColor.RED)));
            return;
        }

        Player player = (Player) sender;
        giveKit(player);
        player.sendMessage(PREFIX.append(Component.text("测试礼包已发放！", NamedTextColor.GREEN)));
    }

    // 日志方法
    private void logStartupInfo() {
        getLogger().info("----------------------------------------");
        getLogger().info("          NewJoinKit 1.0.0 已启动");
        getLogger().info("          适配版本: 1.20.1 (Java 17)");
        getLogger().info("          作者: MCanying");
        getLogger().info("----------------------------------------");
    }

    private void logShutdownInfo() {
        getLogger().info("----------------------------------------");
        getLogger().info("          NewJoinKit 1.0.0 已关闭");
        getLogger().info("          作者: MCanying");
        getLogger().info("----------------------------------------");
    }
}