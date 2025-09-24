package com.kingpixel.ultraeconomy.mixins;

import net.minecraft.util.UserCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(UserCache.class)
public interface UserCacheMixin {

  @Accessor("byName")
  Map<String, ?> getByName();

  @Accessor("byUuid")
  Map<UUID, ?> getByUuid();


}
