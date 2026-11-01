package com.sandevistan.mixin.client;

import com.sandevistan.TimeDilation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * O servidor só manda a posição de um mob a cada N ticks (ele só se move nesse ritmo).
 * Em vez do cliente interpolar em 3 ticks (movimento em "soluços"), interpolamos em N ticks:
 * o mob desliza de forma contínua na velocidade reduzida.
 */
@Mixin(Entity.class)
public abstract class EntityInterpolationMixin {
    @ModifyVariable(method = "updateTrackedPositionAndAngles", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sandevistan$stretch(int steps) {
        Entity self = (Entity) (Object) this;
        int f = TimeDilation.factorFor(self.getWorld(), self);
        return f > 1 ? Math.max(steps, f) : steps;
    }
}
