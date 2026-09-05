package com.example.bookmod;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the on-disk registry of books (one YAML file per book under
 * plugins/BookMod/books/) and tracks who currently has a book "checked out"
 * for editing. The checkout state is purely in-memory - it is never written
 * to disk and never represents a real inventory slot beyond the single,
 * temporary off-hand placement made while a player is actively editing.
 */
public class BookManager {

    private final JavaPlugin plugin;
    private final File booksFolder;
    private final Map<String, Book> books = new HashMap<>(); // key: name.toLowerCase()

    private final Map<UUID, String> activeEdits = new HashMap<>();     // player -> book name
    private final Map<UUID, ItemStack> savedOffhand = new HashMap<>(); // player -> item to restore

    public BookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.booksFolder = new File(plugin.getDataFolder(), "books");
        if (!booksFolder.exists()) {
            booksFolder.mkdirs();
        }
        loadAll();
    }

    private void loadAll() {
        File[] files = booksFolder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            String name = yml.getString("name");
            String ownerStr = yml.getString("owner");
            if (name == null || ownerStr == null) continue;

            UUID owner = UUID.fromString(ownerStr);
            List<String> pages = yml.getStringList("pages");
            Map<UUID, AccessLevel> shared = new HashMap<>();

            if (yml.isConfigurationSection("shared")) {
                for (String key : yml.getConfigurationSection("shared").getKeys(false)) {
                    try {
                        shared.put(UUID.fromString(key),
                                AccessLevel.valueOf(yml.getString("shared." + key)));
                    } catch (IllegalArgumentException ignored) {
                        // corrupt entry, skip it
                    }
                }
            }

            Book book = new Book(name, owner, pages, shared);
            books.put(name.toLowerCase(Locale.ROOT), book);
        }
    }

    public Book get(String name) {
        return books.get(name.toLowerCase(Locale.ROOT));
    }

    public java.util.Collection<Book> getAll() {
        return books.values();
    }

    public boolean exists(String name) {
        return books.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Book create(String name, UUID owner) {
        Book book = new Book(name, owner);
        books.put(name.toLowerCase(Locale.ROOT), book);
        save(book);
        return book;
    }

    public void remove(Book book) {
        books.remove(book.getName().toLowerCase(Locale.ROOT));
        File f = fileFor(book.getName());
        if (f.exists()) {
            f.delete();
        }
    }

    public void rename(Book book, String newName) {
        books.remove(book.getName().toLowerCase(Locale.ROOT));
        File old = fileFor(book.getName());
        if (old.exists()) {
            old.delete();
        }
        book.setName(newName);
        books.put(newName.toLowerCase(Locale.ROOT), book);
        save(book);
    }

    public void save(Book book) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("name", book.getName());
        yml.set("owner", book.getOwner().toString());
        yml.set("pages", book.getPages());
        for (Map.Entry<UUID, AccessLevel> e : book.getShared().entrySet()) {
            yml.set("shared." + e.getKey(), e.getValue().name());
        }
        try {
            yml.save(fileFor(book.getName()));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save book '" + book.getName() + "': " + ex.getMessage());
        }
    }

    private File fileFor(String name) {
        return new File(booksFolder, name.toLowerCase(Locale.ROOT) + ".yml");
    }

    // ---- transient edit-session helpers (never persisted) ----

    public void startEditSession(UUID player, String bookName, ItemStack previousOffhand) {
        activeEdits.put(player, bookName);
        savedOffhand.put(player, previousOffhand);
    }

    public String getActiveEdit(UUID player) {
        return activeEdits.get(player);
    }

    public ItemStack takeSavedOffhand(UUID player) {
        return savedOffhand.remove(player);
    }

    public void endEditSession(UUID player) {
        activeEdits.remove(player);
    }
}
