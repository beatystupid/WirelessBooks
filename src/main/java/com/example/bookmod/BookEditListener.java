package com.example.bookmod;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class BookEditListener implements Listener {

    private final BookPlugin plugin;
    private final BookManager manager;

    public BookEditListener(BookPlugin plugin, BookManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onEditBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        String bookName = manager.getActiveEdit(player.getUniqueId());
        if (bookName == null) {
            // Not a book this plugin handed out - leave it alone entirely.
            return;
        }

        Book book = manager.get(bookName);
        if (book != null) {
            book.setPages(event.getNewBookMeta().getPages());
            manager.save(book);
        }

        if (event.isSigning()) {
            // Cancelling here stops the item from ever becoming a signed
            // written_book. The private error goes only to this player.
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "This book can't be signed - it stays editable in the online library.",
                    NamedTextColor.RED));
        }

        // The edit "session" for this GUI is over either way (Done or Sign
        // both close the screen), so clean up on the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> restore(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.getActiveEdit(player.getUniqueId()) != null) {
            restore(player);
        }
    }

    private void restore(Player player) {
        ItemStack previous = manager.takeSavedOffhand(player.getUniqueId());
        player.getInventory().setItemInOffHand(previous);
        manager.endEditSession(player.getUniqueId());
    }
}
