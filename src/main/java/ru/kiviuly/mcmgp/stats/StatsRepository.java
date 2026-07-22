package ru.kiviuly.mcmgp.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Глобальная статистика игроков в SQLite (stats.db). Запись — асинхронно; чтение —
 * асинхронно с callback в main thread. Драйвер org.sqlite встроен в Paper (shade
 * не нужен). Свои счётчики: добавь колонку в CREATE + {@link #COLUMNS} и вызывай
 * {@link #add}. Базовый набор — wins/loses/kills/played.
 */
public class StatsRepository
{
    /** Разрешённые колонки-счётчики (whitelist против SQL-инъекции в имени колонки). */
    public static final Set<String> COLUMNS = Set.of("wins", "loses", "kills", "played");

    public record Row(String name, int wins, int loses, int kills, int played) {}

    private final JavaPlugin plugin;
    private Connection connection;

    public StatsRepository(JavaPlugin plugin) {this.plugin = plugin;}

    public void open() throws SQLException
    {
        File db = new File(plugin.getDataFolder(), "stats.db");
        db.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement st = connection.createStatement())
        {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stats (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    wins INTEGER NOT NULL DEFAULT 0,
                    loses INTEGER NOT NULL DEFAULT 0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    played INTEGER NOT NULL DEFAULT 0
                )""");
        }
    }

    public void close()
    {
        try {if (connection != null) {connection.close();}}
        catch (SQLException ignored) {}
    }

    private void ensureRow(UUID uuid, String name) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stats(uuid, name) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name"))
        {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    /** Итог матча одному игроку: +1 к played, +1 к wins или loses, +kills. */
    public void recordMatch(UUID uuid, String name, boolean won, int kills)
    {
        async(() ->
        {
            ensureRow(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE stats SET played = played + 1, wins = wins + ?, loses = loses + ?, kills = kills + ? WHERE uuid = ?"))
            {
                ps.setInt(1, won ? 1 : 0);
                ps.setInt(2, won ? 0 : 1);
                ps.setInt(3, kills);
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Увеличить произвольный счётчик из whitelist. */
    public void add(UUID uuid, String name, String column, int delta)
    {
        if (!COLUMNS.contains(column)) {throw new IllegalArgumentException("Bad column " + column);}
        async(() ->
        {
            ensureRow(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE stats SET " + column + " = " + column + " + ? WHERE uuid = ?"))
            {
                ps.setInt(1, delta);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Прочитать статистику по нику (async; callback в main thread; row == null если нет). */
    public void findByName(String name, Consumer<Row> callback)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            Row row = null;
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM stats WHERE name = ? COLLATE NOCASE"))
            {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        row = new Row(rs.getString("name"), rs.getInt("wins"),
                            rs.getInt("loses"), rs.getInt("kills"), rs.getInt("played"));
                    }
                }
            }
            catch (SQLException e)
            {
                plugin.getLogger().severe("Stats read error: " + e.getMessage());
            }
            Row result = row;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    private interface SqlRunnable {void run() throws SQLException;}

    private void async(SqlRunnable action)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            try {synchronized (this) {action.run();}}
            catch (SQLException e) {plugin.getLogger().severe("Stats write error: " + e.getMessage());}
        });
    }
}
