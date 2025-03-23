package midnighttale.voidrespawn.mixin;

import midnighttale.voidrespawn.Voidrespawn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(Entity.class)
public class EntityMixin {

    /**
     * Intercept when an entity falls below the world and teleport it instead of killing it
     */
    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void onFallBelowWorld(CallbackInfo ci) {
        if (voidrespawn$shouldTeleportFromVoid()) {
            voidrespawn$teleportFromVoid();
            ci.cancel(); // Prevent the original method from executing
        }
    }
    
    /**
     * Also intercept the checkOutOfWorld method which is called when an entity is below Y=-64
     */
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void onCheckBelowWorld(CallbackInfo ci) {
        if (voidrespawn$shouldTeleportFromVoid()) {
            voidrespawn$teleportFromVoid();
            ci.cancel(); // Prevent the original method from executing
        }
    }
    
    /**
     * Check if an entity should be teleported from the void
     */
    @Unique
    private boolean voidrespawn$shouldTeleportFromVoid() {
        Entity self = (Entity)(Object)this;
        // Only teleport from dimensions other than the Overworld AND when below Y=-60 (in the void)
        return self.level().dimension() != Level.OVERWORLD && self.getY() < -60.0;
    }
    
    /**
     * Common method to handle teleportation from void
     */
    @Unique
    private void voidrespawn$teleportFromVoid() {
        Entity self = (Entity)(Object)this;
        
        // Skip if we're on the client side
        if (self.level().isClientSide()) {
            return;
        }
            
        // Get the server level
        ServerLevel overworld = null;
        if (self instanceof ServerPlayer serverPlayer) {
            overworld = Objects.requireNonNull(serverPlayer.getServer()).getLevel(Level.OVERWORLD);
        } else if (self.level() instanceof ServerLevel serverLevel) {
            overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
        }
        
        if (overworld != null) {
            // Teleport to same X,Z coordinates but at Y=320 in Overworld
            double x = self.getX();
            double z = self.getZ();
            float yRot = self.getYRot();
            float xRot = self.getXRot();
            
            // Play teleport exit sound at the source dimension
            self.level().playSound(null, self.getX(), -60, self.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
            
            if (self instanceof ServerPlayer serverPlayer) {
                // Apply blindness effect for 5 seconds (100 ticks)
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                
                // Apply slow falling for 10 seconds (200 ticks) to prevent fall damage
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
                
                // Teleport the player
                serverPlayer.teleportTo(overworld, x, 1024, z, yRot, xRot);
                
                // Play teleport arrival sound at the destination
                overworld.playSound(null, x, 1024, z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
            } else {
                // Handle non-player entities
                Entity newEntity = self.getType().create(overworld);
                if (newEntity != null) {
                    newEntity.copyPosition(self);
                    newEntity.setPos(x, 1024, z);
                    newEntity.setYRot(yRot);
                    newEntity.setXRot(xRot);
                    overworld.addFreshEntity(newEntity);
                    self.discard();
                    
                    // Play teleport arrival sound at the destination
                    overworld.playSound(null, x, 1024, z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
                }
            }
        }
    }
} 