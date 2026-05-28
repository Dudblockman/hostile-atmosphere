package org.dudblockman.hostileatmosphere.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.data.AtmosphereClientData;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SyncAirDebtPayload.TYPE,
                SyncAirDebtPayload.CODEC,
                (payload, ctx) -> AtmosphereClientData.setAirDebt(ctx.player().getUUID(), payload.airDebt())
        );

        registrar.playToClient(
                SyncToxinPayload.TYPE,
                SyncToxinPayload.CODEC,
                (payload, ctx) -> AtmosphereClientData.setToxin(ctx.player().getUUID(), payload.toxinLevel())
        );
    }
}
