package midnighttale.voidrespawn.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Random;

@Mixin(Entity.class)
public class EntityMixin {

    // Add random for particle positioning
    @Unique
    private static final Random voidrespawn$RANDOM = new Random();

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
     * Check if player should receive darkness effect when falling
     */
    @Unique
    private boolean voidrespawn$shouldApplyDarknessEffect() {
        Entity self = (Entity)(Object)this;
        // Apply darkness in dimensions other than Overworld when between Y=-32 and Y=-60
        return self.level().dimension() != Level.OVERWORLD && self.getY() < -32.0 && self.getY() > -60.0;
    }
    
    /**
     * Inject to check if player is falling and should get darkness effect
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (voidrespawn$shouldApplyDarknessEffect()) {
            Entity self = (Entity)(Object)this;
            if (self instanceof ServerPlayer serverPlayer) {
                // Apply darkness effect for 5 seconds (40 ticks)
                MobEffectInstance darkness = new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false);
                serverPlayer.addEffect(darkness);
            }
        }
    }
    
    /**
     * Create particle effect at entity location
     */
    @Unique
    private void voidrespawn$spawnTeleportParticles(ServerLevel level, double x, double y, double z) {
        // Spawn a cluster of particles
        for (int i = 0; i < 50; i++) {
            double offsetX = voidrespawn$RANDOM.nextGaussian() * 0.5;
            double offsetY = voidrespawn$RANDOM.nextGaussian() * 0.5;
            double offsetZ = voidrespawn$RANDOM.nextGaussian() * 0.5;
            
            // Mix of particles for a dramatic effect
            level.sendParticles(
                ParticleTypes.PORTAL,
                x + offsetX, 
                y + offsetY,
                z + offsetZ,
                1, 0, 0, 0, 0);
            
            // Add some end rod particles for a bright effect
            if (i % 5 == 0) {
                level.sendParticles(
                    ParticleTypes.END_ROD,
                    x + offsetX,
                    y + offsetY,
                    z + offsetZ,
                    1, 0, 0, 0, 0);
            }
        }
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
            
            // Spawn particles at current position (in void)
            if (self.level() instanceof ServerLevel sourceLevel) {
                voidrespawn$spawnTeleportParticles(sourceLevel, self.getX(), -60, self.getZ());
            }
            
            // Play teleport exit sound at the source dimension
            self.level().playSound(null, self.getX(), -60, self.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
            
            if (self instanceof ServerPlayer serverPlayer) {
                // Apply blindness effect for 5 seconds (100 ticks)
                MobEffectInstance blindness = new MobEffectInstance(MobEffects.DARKNESS, 300, 0, false, false);
                serverPlayer.addEffect(blindness);
                
                // Apply slow falling for 3 seconds (60 ticks) to prevent fall damage
                MobEffectInstance slowFalling = new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false);
                serverPlayer.addEffect(slowFalling);
                
                // Teleport the player
                serverPlayer.teleportTo(overworld, x, 1024, z, yRot, xRot);
                
                // Spawn particles at destination
                voidrespawn$spawnTeleportParticles(overworld, x, 1024, z);
                
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
                    
                    // Spawn particles at destination
                    voidrespawn$spawnTeleportParticles(overworld, x, 1024, z);
                    
                    // Play teleport arrival sound at the destination
                    overworld.playSound(null, x, 1024, z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
                }
            }
        }
    }
} 