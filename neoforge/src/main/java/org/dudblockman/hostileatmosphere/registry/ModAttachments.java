package org.dudblockman.hostileatmosphere.registry;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.engine.EntityHazardEngine;

import net.neoforged.neoforge.registries.DeferredHolder;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerAtmosphereData>> ATMOSPHERE_DATA =
            ATTACHMENT_TYPES.register("atmosphere_data", () ->
                    AttachmentType.builder(PlayerAtmosphereData::new)
                            .serialize(PlayerAtmosphereData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EntityHazardEngine.EntityAirState>> ENTITY_AIR_STATE =
            ATTACHMENT_TYPES.register("entity_air_state", () ->
                    AttachmentType.builder(() -> EntityHazardEngine.EntityAirState.ZERO).build()
            );
}
