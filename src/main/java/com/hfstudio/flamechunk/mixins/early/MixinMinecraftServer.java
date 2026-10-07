package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.crash.CrashReport;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.server.sampler.CrashReportDiagnostics;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "tick", at = @At("HEAD"))
    public void flamechunk$beginWorkTick(CallbackInfo callback) {
        PerformanceSampler.beginServerWorkTick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void flamechunk$endWorkTick(CallbackInfo callback) {
        PerformanceSampler.endServerWorkTick();
    }

    @Inject(method = "addServerInfoToCrashReport", at = @At("RETURN"))
    public void flamechunk$appendTicketDiagnostics(CrashReport report, CallbackInfoReturnable<CrashReport> callback) {
        CrashReportDiagnostics.append(callback.getReturnValue(), (MinecraftServer) (Object) this);
    }
}
