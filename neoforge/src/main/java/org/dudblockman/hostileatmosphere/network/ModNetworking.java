package org.dudblockman.hostileatmosphere.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.dudblockman.hostileatmosphere.Constants;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(SyncAirDebtPayload.TYPE,    SyncAirDebtPayload.CODEC,
                (payload, ctx) -> PacketHandlers.onAirDebt(payload, ctx.player().getUUID()));

        registrar.playToClient(SyncToxinPayload.TYPE,      SyncToxinPayload.CODEC,
                (payload, ctx) -> PacketHandlers.onToxin(payload, ctx.player().getUUID()));

        registrar.playToClient(SyncDivingActivePayload.TYPE, SyncDivingActivePayload.CODEC,
                (payload, ctx) -> PacketHandlers.onDivingActive(payload, ctx.player().getUUID()));

        registrar.playToClient(SyncZoneSeverityPayload.TYPE, SyncZoneSeverityPayload.CODEC,
                (payload, ctx) -> PacketHandlers.onZoneSeverity(payload, ctx.player().getUUID()));
    }
}
