package org.fiftic;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion; // Импортируем PlaceholderAPI для регистрации
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.sql.*;

public class VarNumPlugin extends JavaPlugin implements CommandExecutor {
    private static Connection connection;

    /*
    if (getConfigData("type-db").equals("SQL")) {

    } else if (getConfigData("type-db").equals("MYSQL")) {

    }
    */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        File dbFile = new File(dataFolder, "varnum.db");
        if (getConfig().getString("type-db").equals("SQL") || getConfig().getString("type-db").equals("MYSQL")) {
            getLogger().info("Connecting to " + getConfig().getString("type-db"));
        } else {
            getLogger().severe("You have specified a value different in config.yml(type-db) from SQL or MYSQL. Write in 'type-db' the value 'SQL' or 'MYSQL'");
        }
        connectToDatabase(dbFile);

        this.getCommand("varnum").setExecutor(this);

        new VarNumPlaceholder().register();
        getLogger().info("VarNumPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        getLogger().info("VarNumPlugin has been disabled!");
    }

    public String getConfigData(String data) {
        return getConfig().getString(data);
    }

    //функция проверки существования таблицы
    private boolean doesTableExist(String tableName) throws SQLException {
        try {
            String checkTableSQL = "";
            if (getConfigData("type-db").equals("SQL")) {
                checkTableSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name = ?;";
            } else if (getConfigData("type-db").equals("MYSQL")) {
                //checkTableSQL = "SHOW TABLES LIKE ?;";
                checkTableSQL = "SELECT name FROM INFORMATION_SCHEMA.TABLES WHERE name = ?;";
            }
            PreparedStatement stmt = connection.prepareStatement(checkTableSQL);
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery(checkTableSQL);
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // функция проверки на существование записи о игроке в таблице
    private boolean doesPlayerInTableExist(String tableName, String player) {
        String playerCheckSQL = "SELECT COUNT(*) FROM ? WHERE player = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(playerCheckSQL)) {
            stmt.setString(1, tableName);
            stmt.setString(2, player);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0; // Возвращает true, если игрок найден
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }


    private void connectToDatabase(File dbFile) {
        if (getConfigData("type-db").equals("SQL")) {
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            try {
                connection = DriverManager.getConnection(url);
                getLogger().info("Successful connection to SQL");
            } catch (SQLException e) {
                getLogger().info("Failed to connection to SQL due to error: " + e.getMessage());
            }
        } else if (getConfigData("type-db").equals("MYSQL")) {
            String url = "jdbc:mysql://" + getConfigData("mysql.host") + "/" + getConfigData("mysql.database");
            String user = getConfigData("mysql.user");
            String password = getConfigData("mysql.password");

            try {
                connection = DriverManager.getConnection(url, user, password);
                getLogger().info("Successful connection to MYSQL");
            } catch (SQLException e) {
                getLogger().severe("Failed to connection to MYSQL due to error: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("varnum") && (sender.isOp() || sender instanceof ConsoleCommandSender)) {
            if (args.length == 0) {
                sender.sendMessage("Use /varnum <create/add/set/delete>");
                return false;
            }
            String action = args[0];
            if (action.equals("reload")) {
                reloadConfig();
                sender.sendMessage("[VarNum] Config successfully reloaded!");
                return true;
            }

            if (action.equals("create") || action.equals("delete")) {
                if (args.length != 2) {
                    sender.sendMessage("Use /varnum <create/delete> <var name>");
                    return  false;
                }
                String varname = args[1];
                if (action.equals("create")) {
                    // код создания таблицы в базе данных с именем varmane
                    String createTableSQL = "CREATE TABLE IF NOT EXISTS ? (player TEXT, value TEXT);";
                    try {
                        PreparedStatement stmt = connection.prepareStatement(createTableSQL);
                        stmt.setString(1, varname);
                        stmt.executeUpdate(createTableSQL);
                        sender.sendMessage("[VarNum] New variable '" + varname + "' successfully created");
                        return true;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                if (action.equals("delete")) {
                    // код удаления таблицы в базе данных с именем varmane
                    String deleteTableSQL = "DROP TABLE IF EXISTS ?;";
                    try {
                        PreparedStatement stmt = connection.prepareStatement(deleteTableSQL);
                        stmt.setString(1, varname);
                        stmt.executeUpdate(deleteTableSQL);
                        sender.sendMessage("[VarNum] Variable '" + varname + "' successfully deleted");
                        return true;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            if (action.equals("rename")) {
                // код переименования таблицы в базе данных с именем varmane
                if (args.length != 3) {
                    sender.sendMessage("Use /varnum rename <var name> <new var name>");
                    return  false;
                }

                String varname = args[1];
                String newvarname = args[2];
                String renameTableSQL = "ALTER TABLE ? RENAME TO ?;";

                // проверяем существование таблицы
                try {
                    if (doesTableExist(varname)) {
                        if (!doesTableExist(newvarname)) {
                            PreparedStatement stmt = connection.prepareStatement(renameTableSQL);
                            stmt.setString(1, varname);
                            stmt.setString(2, newvarname);
                            connection.createStatement().executeUpdate(renameTableSQL);
                            sender.sendMessage("[Varnum] Variable '" + varname + "' has been successfully renamed to '" + newvarname + "'");
                            return true;
                        } else {
                            sender.sendMessage("[Varnum] Variable '" + newvarname + "' already exists");
                            return false;
                        }
                    } else {
                        sender.sendMessage("[Varnum] The variable '" + varname + "' does not exist");
                        return false;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            }


            if (action.equals("set")) {
                if (args.length != 4) {
                    //sender.sendMessage("Use /varnum set <var name> <player> <value (from -2147483648 to 2147483647) or delete>");
                    return false;
                }

                String value = args[3]; //3
                String playername = args[2]; //fiftic
                String varname = args[1]; //fiftic

                // проверяем существование переменной
                try {
                    if (doesTableExist(varname)) {
                        if (value.equals("delete")) {
                            // удаление игрока из переменной
                            if (doesPlayerInTableExist(varname, playername)) {
                                String deletePlayerFromVarSQL = "DELETE FROM " + varname + " WHERE player = '" + playername + "';";
                                try (PreparedStatement stmt = connection.prepareStatement(deletePlayerFromVarSQL)) {
                                    stmt.setString(1, playername);
                                    stmt.executeUpdate();
                                    sender.sendMessage("[Varnum] Variable '" + varname + "' of player '" + playername + "' successfully deleted");
                                    return true;
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    return false;
                                }
                            } else {
                                sender.sendMessage("[VarNum] Player '" + playername + "' does not exist in variable '" + varname + "'");
                                return false;
                            }
                        } else {
                            // попытаемся преобразовать value в число
                            int intValue = 0;
                            try {
                                intValue = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("[VarNum] Value is not a valid integer.");
                                return false;
                            }
                            String endValue = Integer.toString(intValue);
                            // устанавливаем значение в переменной varname для игрока
                            String setValueSQL = "";
                            PreparedStatement stmt;
                            if (doesPlayerInTableExist(varname, playername)) {
                                setValueSQL = "UPDATE ? SET value = ? WHERE player = ?;";
                                stmt = connection.prepareStatement(setValueSQL);
                                stmt.setString(1, varname);
                                stmt.setString(2, endValue);
                                stmt.setString(3, playername);
                                sender.sendMessage("1");
                            } else {
                                setValueSQL = "INSERT INTO ? (player, value) VALUES (? , ?);";
                                stmt = connection.prepareStatement(setValueSQL);
                                stmt.setString(1, varname);
                                stmt.setString(2, playername);
                                stmt.setString(3, endValue);
                                sender.sendMessage("2");
                            }
                            stmt.executeUpdate();
                            sender.sendMessage("[Varnum] Variable '" + varname + "' for " + playername + " is set to " + value);
                            return true;
                        }
                    } else {
                        sender.sendMessage("[VarNum] Variable '" + varname + "' does not exist.");
                        return false;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            sender.sendMessage("Use /varnum <create/delete/rename/set>");
            return false;
        }
        return false;
    }

    public static String VarNumPlaceholderValue(String varname, String player) {
        String getValueSQL = "SELECT value FROM ? WHERE player = ?;";
        try {
            PreparedStatement stmt = connection.prepareStatement(getValueSQL);
            stmt.setString(1, varname);
            stmt.setString(2, player);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }
}

//код плейсхолдера
class VarNumPlaceholder extends PlaceholderExpansion {
    @Override
    public String getIdentifier() {
        return "varnum";
    }
    @Override
    public String getAuthor() {
        return "fiftic"; // Укажите автора плагина
    }
    @Override
    public String getVersion() {
        return "1.0"; // Версия вашего плагина
    }
    @Override
    public boolean persist() {
        return false; // Не сохраняем
    }
    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        // Проверяем, идет ли речь о вашем плейсхолдере
        if (identifier.startsWith("varnum_")) {
            String varname = identifier.substring(8); // Получаем значение

            // Убираем фигурные скобки, если они есть
            if (varname.startsWith("{") && varname.endsWith("}")) {
                varname = varname.substring(1, varname.length() - 1);
            }

            // Получаем значение плейсхолдера
            String result = getPlaceholderValue(varname, player.getName());
            // Возвращаем результат или пустую строку
            if (result != null) {
                return result;
            } else {
                return "";
            }
        }
        return null; // Возвращаем null, если плейсхолдер не найден
    }

    private String getPlaceholderValue(String varname, String player) {
        // Проверяем, является ли значение плейсхолдером
        if (varname.startsWith("{") && varname.endsWith("}")) {
            varname = varname.substring(1, varname.length() - 1);
            varname = parseOtherPlaceholder(varname); // Обрабатываем вложенный плейсхолдер
            varname = VarNumPlugin.VarNumPlaceholderValue(varname, player);
            return varname;
        }
        varname = VarNumPlugin.VarNumPlaceholderValue(varname, player);
        return varname; // Возвращаем просто текст
    }

    private String parseOtherPlaceholder(String identifier) {
        // Обработка других плейсхолдеров, если это необходимо
        // Вы можете использовать PlaceholderAPI для получения значений
        return PlaceholderAPI.setPlaceholders(null, "%" + identifier + "%");
    }
}
