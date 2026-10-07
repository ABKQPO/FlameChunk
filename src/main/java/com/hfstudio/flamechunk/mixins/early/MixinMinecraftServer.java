package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.crash.CrashReport;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.server.sampler.CrashReportDiagnostics;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "addServerInfoToCrashReport", at = @At("RETURN"))
    public void flamechunk$appendTicketDiagnostics(CrashReport report, CallbackInfoReturnable<CrashReport> callback) {
        CrashReportDiagnostics.append(callback.getReturnValue(), (MinecraftServer) (Object) this);
    }
}
