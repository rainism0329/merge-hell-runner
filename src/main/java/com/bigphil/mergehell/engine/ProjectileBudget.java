package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.model.Projectile;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Atomic whole-volley admission, before allocating projectiles or drawing combat randomness. */
public final class ProjectileBudget {
    private ProjectileBudget() { }

    /**
     * Records a denied attempt without constructing a shot. Does not reserve space: callers
     * must insert immediately on the same simulation thread before another producer runs.
     */
    public static boolean canEmit(List<Projectile> destination, int count) {
        Objects.requireNonNull(destination, "destination");
        if (count < 0) throw new IllegalArgumentException("Negative volley size");
        return !(destination instanceof ProjectileBuffer buffer) || buffer.canEmit(count);
    }

    public static boolean emit(List<Projectile> destination, int count, Supplier<List<Projectile>> factory) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(factory, "factory");
        if (count < 0) throw new IllegalArgumentException("Negative volley size");
        if (destination instanceof ProjectileBuffer buffer) return buffer.tryEmit(count, factory);
        if (count == 0) return true;
        List<Projectile> created = List.copyOf(Objects.requireNonNull(factory.get(), "volley"));
        if (created.size() != count) throw new IllegalArgumentException("Volley size differs from admission request");
        return destination.addAll(created);
    }

    public static boolean emitOne(List<Projectile> destination, Supplier<Projectile> factory) {
        Objects.requireNonNull(factory, "factory");
        return emit(destination, 1, () -> List.of(factory.get()));
    }
}
