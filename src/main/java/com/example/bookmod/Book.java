package com.example.bookmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure data holder for one online book. Never an ItemStack, never placed in
 * any inventory or container - this is the only "real" copy of the book.
 */
public class Book {

    private String name;
    private final UUID owner;
    private List<String> pages;
    private final Map<UUID, AccessLevel> shared;

    public Book(String name, UUID owner) {
        this(name, owner, new ArrayList<>(Collections.singletonList("")), new HashMap<>());
    }

    public Book(String name, UUID owner, List<String> pages, Map<UUID, AccessLevel> shared) {
        this.name = name;
        this.owner = owner;
        this.pages = pages;
        this.shared = shared;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    public List<String> getPages() {
        return pages;
    }

    public void setPages(List<String> pages) {
        this.pages = pages;
    }

    public Map<UUID, AccessLevel> getShared() {
        return shared;
    }
}
