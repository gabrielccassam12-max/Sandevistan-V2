package com.sandevistan.mixin;

import com.sandevistan.TimeDilation;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Ponto único por onde TODA entidade (vanilla ou de outro mod) é "ticada" tanto no
 * ServerWorld quanto no ClientWorld. Cancelar aqui = a entidade não atualiza neste tick.
 */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void sandevistan$dilate(Consumer<T> tickConsumer, T entity, CallbackInfo ci) {
        World self = (World) (Object) this;
        if (TimeDilation.shouldSkip(self, entity)) {
            TimeDilation.onSkipped(entity);
            ci.cancel();
        }
    }
}
