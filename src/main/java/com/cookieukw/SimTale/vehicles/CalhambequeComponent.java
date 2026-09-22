package com.cookieukw.SimTale.vehicles;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Component holding state and physics variables for the 1930s Calhambeque vintage car.
 */
public class CalhambequeComponent implements Component<EntityStore> {

    public static final BuilderCodec<CalhambequeComponent> CODEC = BuilderCodec.builder(CalhambequeComponent.class, CalhambequeComponent::new)
            .append(new KeyedCodec<>("DriverUuid", Codec.STRING),
                    (c, s) -> c.driverUuid = (s != null && !s.isEmpty()) ? UUID.fromString(s) : null,
                    c -> c.driverUuid != null ? c.driverUuid.toString() : null)
            .add()
            .append(new KeyedCodec<>("Speed", Codec.FLOAT),
                    (c, s) -> c.speed = s != null ? s : 0f,
                    c -> c.speed)
            .add()
            .append(new KeyedCodec<>("EngineRunning", Codec.BOOLEAN),
                    (c, s) -> c.engineRunning = s != null ? s : false,
                    c -> c.engineRunning)
            .add()
            .build();

    public UUID driverUuid;
    public UUID passengerUuid;
    public float speed = 0f;
    public float steerAngle = 0f;
    public float velocityY = 0f;
    public boolean engineRunning = false;
    public long lastHonkTick = 0L;
    public String currentAnim = null;

    /** Seat position for the driver (left-hand steering) relative to vehicle origin. */
    public final Vector3f driverSeatOffset = new Vector3f(0.1f, 1.08f, -1.0f);

    /** Seat position for a passenger (right-hand front bench). */
    public final Vector3f passengerSeatOffset = new Vector3f(0.75f, 1.08f, -1.55f);

    public CalhambequeComponent() {
    }

    public boolean hasDriver() {
        return driverUuid != null;
    }

    @Override
    public Component<EntityStore> clone() {
        CalhambequeComponent copy = new CalhambequeComponent();
        copy.driverUuid = this.driverUuid;
        copy.passengerUuid = this.passengerUuid;
        copy.speed = this.speed;
        copy.steerAngle = this.steerAngle;
        copy.velocityY = this.velocityY;
        copy.engineRunning = this.engineRunning;
        copy.lastHonkTick = this.lastHonkTick;
        return copy;
    }
}
