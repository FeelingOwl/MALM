package com.feelingowl.malm.layer;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Set;

/** A list of exact ids and {@code #tags}, as written in {@code targets} or {@code biomes}. Matches if any entry matches. */
public record HolderFilter(Set<ResourceLocation> ids, List<TagKey<?>> tags) {

    public static final HolderFilter ANY = new HolderFilter(Set.of(), List.of());

    /** An empty filter means "no condition" when used as an extra condition such as {@code biomes}. */
    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> boolean matches(Holder<T> holder) {
        var key = holder.unwrapKey();
        if (key.isPresent() && ids.contains(key.get().location())) {
            return true;
        }
        for (TagKey tag : tags) {
            if (holder.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
