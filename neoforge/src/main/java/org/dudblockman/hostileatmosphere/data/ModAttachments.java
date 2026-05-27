package org.dudblockman.hostileatmosphere.data;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.dudblockman.hostileatmosphere.Constants;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MOD_ID);

    public static final Supplier<AttachmentType<PlayerAtmosphereData>> ATMOSPHERE_DATA =
            ATTACHMENT_TYPES.register("atmosphere_data", () ->
                    AttachmentType.builder(PlayerAtmosphereData::new)
                            .serialize(PlayerAtmosphereData.CODEC)
                            .copyOnDeath()
                            .build()
            );
}
