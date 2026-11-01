package com.sandevistan.mixin.client;

import com.sandevistan.TimeDilation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingInterpolationMixin {
    @ModifyVariable(method = "updateTrackedPositionAndAngles", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sandevistan$stretchBody(int steps) {
        return stretch(steps);
    }

    @ModifyVariable(method = "updateTrackedHeadRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sandevistan$stretchHead(int steps) {
        return stretch(steps);
    }

    private int stretch(int steps) {
        Entity self = (Entity) (Object) this;
        int f = TimeDilation.factorFor(self.getWorld(), self);
        return f > 1 ? Math.max(steps, f) : steps;
    }
}
