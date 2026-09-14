package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.model.Projectile;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Supplier;

/**
 * Simulation-thread projectile storage. Admission never evicts an existing projectile.
 * Use {@link ProjectileBudget} for normal gameplay rejection; ordinary List insertion
 * throws when full so List/iterator/subList contracts are not silently weakened.
 */
public final class ProjectileBuffer extends AbstractList<Projectile> implements RandomAccess {
    private final ArrayList<Projectile> values;
    private final int capacity;
    private long rejectedProjectiles;
    private long rejectedVolleys;

    public ProjectileBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        values = new ArrayList<>(capacity);
    }

    public int capacity() { return capacity; }
    public int remainingCapacity() { return capacity - size(); }
    public long rejectedProjectiles() { return rejectedProjectiles; }
    public long rejectedVolleys() { return rejectedVolleys; }

    @Override public Projectile get(int index) { return values.get(index); }
    @Override public int size() { return values.size(); }
    @Override public Projectile set(int index, Projectile value) {
        return values.set(index, Objects.requireNonNull(value, "projectile"));
    }

    @Override public void add(int index, Projectile value) {
        Objects.checkIndex(index, size() + 1);
        Objects.requireNonNull(value, "projectile");
        requireRoom(1);
        values.add(index, value);
        modCount++;
    }

    @Override public boolean addAll(Collection<? extends Projectile> added) {
        return addAll(size(), added);
    }

    @Override public boolean addAll(int index, Collection<? extends Projectile> added) {
        Objects.checkIndex(index, size() + 1);
        // Snapshot before insertion also makes addAll(this) finite and validates nulls atomically.
        List<Projectile> copy = List.copyOf(Objects.requireNonNull(added, "projectiles"));
        if (copy.isEmpty()) return false;
        requireRoom(copy.size());
        values.addAll(index, copy);
        modCount++;
        return true;
    }

    @Override public Projectile remove(int index) {
        Projectile removed = values.remove(index);
        modCount++;
        return removed;
    }

    @Override protected void removeRange(int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size());
        if (fromIndex == toIndex) return;
        values.subList(fromIndex, toIndex).clear();
        modCount++;
    }

    /** Bombs, transitions and cleanup clear live shots but retain admission diagnostics. */
    @Override public void clear() { removeRange(0, size()); }

    /** Starts a new diagnostic interval (new level/run/restore), including empty storage. */
    public void reset() {
        clear();
        rejectedProjectiles = 0;
        rejectedVolleys = 0;
    }

    boolean tryEmit(int count, Supplier<List<Projectile>> factory) {
        if (count < 0) throw new IllegalArgumentException("Negative volley size");
        Objects.requireNonNull(factory, "factory");
        if (!hasRoom(count)) return false;
        if (count == 0) return true;
        List<Projectile> created = List.copyOf(Objects.requireNonNull(factory.get(), "volley"));
        if (created.size() != count) throw new IllegalArgumentException("Volley size differs from admission request");
        addAll(created);
        return true;
    }

    boolean canEmit(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative volley size");
        return hasRoom(count);
    }

    private boolean hasRoom(int count) {
        if (count <= remainingCapacity()) return true;
        rejectedProjectiles = saturatedAdd(rejectedProjectiles, count);
        rejectedVolleys = saturatedAdd(rejectedVolleys, 1);
        return false;
    }

    private void requireRoom(int count) {
        if (!hasRoom(count)) throw new IllegalStateException("Projectile capacity exceeded; use ProjectileBudget.emit");
    }

    private static long saturatedAdd(long value, int amount) {
        return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
    }
}
