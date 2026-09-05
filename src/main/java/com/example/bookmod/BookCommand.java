package com.example.bookmod;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /book create <name>
 * /book remove <name>
 * /book edit <name> [name <newname> | contents]      (defaults to "contents")
 * /book access <name> add <player> <view|edit>
 * /book access <name> remove <player>
 *
 * No permission node is registered anywhere - every action is available to
 * every player, no OP required.
 */
public class BookCommand implements CommandExecutor, TabCompleter {

    private final BookPlugin plugin;
    private final BookManager manager;

    public BookCommand(BookPlugin plugin, BookManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 2) {
            error(player, "Usage: /book <create|remove|edit|access> <name> ...");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];

        switch (action) {
            case "create" -> handleCreate(player, name);
            case "remove" -> handleRemove(player, name);
            case "edit" -> handleEdit(player, name, args);
            case "access" -> handleAccess(player, name, args);
            default -> error(player, "Unknown action. Use create, remove, edit, or access.");
        }
        return true;
    }

    private void handleCreate(Player player, String name) {
        if (manager.exists(name)) {
            error(player, "A book named '" + name + "' already exists.");
            return;
        }
        manager.create(name, player.getUniqueId());
        info(player, "Created book '" + name + "'.");
    }

    private void handleRemove(Player player, String name) {
        Book book = manager.get(name);
        if (book == null) {
            error(player, "No book named '" + name + "' exists.");
            return;
        }
        if (!book.getOwner().equals(player.getUniqueId())) {
            error(player, "Only the owner can remove this book.");
            return;
        }
        manager.remove(book);
        info(player, "Removed book '" + name + "'.");
    }

    private void handleEdit(Player player, String name, String[] args) {
        Book book = manager.get(name);
        if (book == null) {
            error(player, "No book named '" + name + "' exists.");
            return;
        }

        AccessLevel level = accessLevelFor(book, player.getUniqueId());
        if (level == null) {
            error(player, "You don't have access to this book.");
            return;
        }

        // /book edit <name> name <newname>  -> rename, requires edit access
        if (args.length >= 3 && args[2].equalsIgnoreCase("name")) {
            if (level != AccessLevel.EDIT) {
                error(player, "You don't have edit access to this book.");
                return;
            }
            if (args.length < 4) {
                error(player, "Usage: /book edit <name> name <newname>");
                return;
            }
            String newName = args[3];
            if (manager.exists(newName)) {
                error(player, "A book named '" + newName + "' already exists.");
                return;
            }
            manager.rename(book, newName);
            info(player, "Renamed book to '" + newName + "'.");
            return;
        }

        // /book edit <name> contents  (or just /book edit <name>) -> open it
        if (level == AccessLevel.EDIT) {
            openForEditing(player, book);
        } else {
            openForViewing(player, book);
        }
    }

    private void handleAccess(Player player, String name, String[] args) {
        Book book = manager.get(name);
        if (book == null) {
            error(player, "No book named '" + name + "' exists.");
            return;
        }
        if (!book.getOwner().equals(player.getUniqueId())) {
            error(player, "Only the owner can manage access to this book.");
            return;
        }
        if (args.length < 4) {
            error(player, "Usage: /book access <name> <add|remove|modify> <player> [view|edit]");
            return;
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        String targetName = args[3];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target.getUniqueId().equals(book.getOwner())) {
            error(player, "The owner already has full access.");
            return;
        }

        if (sub.equals("add")) {
            if (args.length < 5) {
                error(player, "Usage: /book access <name> add <player> <view|edit>");
                return;
            }
            AccessLevel level;
            try {
                level = AccessLevel.valueOf(args[4].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                error(player, "Access level must be 'view' or 'edit'.");
                return;
            }
            book.getShared().put(target.getUniqueId(), level);
            manager.save(book);
            info(player, "Gave " + targetName + " " + level.name().toLowerCase(Locale.ROOT)
                    + " access to '" + book.getName() + "'.");
        } else if (sub.equals("remove")) {
            if (!book.getShared().containsKey(target.getUniqueId())) {
                error(player, targetName + " doesn't have access to '" + book.getName() + "'.");
                return;
            }
            book.getShared().remove(target.getUniqueId());
            manager.save(book);
            info(player, "Removed " + targetName + "'s access to '" + book.getName() + "'.");
        } else if (sub.equals("modify")) {
            AccessLevel current = book.getShared().get(target.getUniqueId());
            if (current == null) {
                error(player, targetName + " doesn't have access to '" + book.getName() + "'.");
                return;
            }
            AccessLevel swapped = current == AccessLevel.VIEW ? AccessLevel.EDIT : AccessLevel.VIEW;
            book.getShared().put(target.getUniqueId(), swapped);
            manager.save(book);
            info(player, "Changed " + targetName + "'s access to '" + book.getName() + "' to "
                    + swapped.name().toLowerCase(Locale.ROOT) + ".");
        } else {
            error(player, "Usage: /book access <name> <add|remove|modify> <player> [view|edit]");
        }
    }

    private AccessLevel accessLevelFor(Book book, UUID uuid) {
        if (book.getOwner().equals(uuid)) {
            return AccessLevel.EDIT;
        }
        return book.getShared().get(uuid);
    }

    /** View-only access: opens the read GUI directly off the item, exactly like a lectern. Never touches inventory. */
    private void openForViewing(Player player, Book book) {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.setTitle(book.getName());
        OfflinePlayer owner = Bukkit.getOfflinePlayer(book.getOwner());
        meta.setAuthor(owner.getName() != null ? owner.getName() : "Unknown");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.setPages(book.getPages());
        stack.setItemMeta(meta);
        player.openBook(stack);
    }

    /**
     * Edit access: the vanilla book-and-quill screen only opens for an item
     * physically in the player's hand, so we place one in their off-hand for
     * the duration of this single edit. BookEditListener restores their
     * original off-hand item and clears the temporary book the instant they
     * close the screen (or disconnect) - it is never left in place.
     */
    private void openForEditing(Player player, Book book) {
        if (manager.getActiveEdit(player.getUniqueId()) != null) {
            error(player, "Finish your current book edit before opening another.");
            return;
        }
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.setPages(book.getPages());
        stack.setItemMeta(meta);

        ItemStack previousOffhand = player.getInventory().getItemInOffHand();
        manager.startEditSession(player.getUniqueId(), book.getName(), previousOffhand);
        player.getInventory().setItemInOffHand(stack);
        info(player, "Right-click the book in your off-hand to edit '" + book.getName()
                + "'. Signing is disabled - use 'Done' to save.");
    }

    private void error(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    private void info(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        UUID uuid = player.getUniqueId();

        if (args.length == 1) {
            return filter(List.of("create", "remove", "edit", "access"), args[0]);
        }

        // Book-name completion: "edit" suggests anything you can open (owned or shared with you);
        // "remove"/"access" suggest only books you own, since only the owner can do those.
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Book book : manager.getAll()) {
                boolean owns = book.getOwner().equals(uuid);
                boolean hasAnyAccess = owns || book.getShared().containsKey(uuid);
                if (action.equals("edit") && hasAnyAccess) {
                    names.add(book.getName());
                } else if ((action.equals("remove") || action.equals("access")) && owns) {
                    names.add(book.getName());
                }
            }
            return filter(names, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return filter(List.of("name", "contents"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("access")) {
            return filter(List.of("add", "remove", "modify"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("access")) {
            Book book = manager.get(args[1]);
            if (book == null) return Collections.emptyList();
            String sub = args[2].toLowerCase(Locale.ROOT);

            if (sub.equals("add")) {
                // Online players who don't already have access.
                List<String> candidates = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getUniqueId().equals(book.getOwner())) continue;
                    if (book.getShared().containsKey(online.getUniqueId())) continue;
                    candidates.add(online.getName());
                }
                return filter(candidates, args[3]);
            }
            if (sub.equals("remove") || sub.equals("modify")) {
                // Players who currently have access.
                List<String> candidates = new ArrayList<>();
                for (UUID sharedUuid : book.getShared().keySet()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(sharedUuid);
                    if (op.getName() != null) candidates.add(op.getName());
                }
                return filter(candidates, args[3]);
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("access") && args[2].equalsIgnoreCase("add")) {
            return filter(List.of("view", "edit"), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.startsWith(p)) out.add(o);
        }
        return out;
    }
}
