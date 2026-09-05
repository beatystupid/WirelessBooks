package com.example.bookmod;

import org.bukkit.plugin.java.JavaPlugin;

public class BookPlugin extends JavaPlugin {

    private BookManager bookManager;

    @Override
    public void onEnable() {
        bookManager = new BookManager(this);
        getCommand("book").setExecutor(new BookCommand(this, bookManager));
        getServer().getPluginManager().registerEvents(new BookEditListener(this, bookManager), this);
    }

    public BookManager getBookManager() {
        return bookManager;
    }
}
