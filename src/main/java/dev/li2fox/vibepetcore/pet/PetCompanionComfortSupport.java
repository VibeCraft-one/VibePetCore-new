package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetCompanionComfortSupport {
    private static final String COMFORT_REST_KEY = "comfort_rest";
    private static final String COMFORT_REST_X_KEY = "comfort_rest_x";
    private static final String COMFORT_REST_Y_KEY = "comfort_rest_y";
    private static final String COMFORT_REST_Z_KEY = "comfort_rest_z";
    private static final double REST_COORDINATE_SCALE = 100.0D;
    private static final double OWNER_REST_RADIUS_SQUARED = 7.0D * 7.0D;
    private static final double OWNER_BUBBLE_RADIUS_SQUARED = 1.15D * 1.15D;

    private PetCompanionComfortSupport() {
    }

    static boolean requestRest(Player owner, RuntimePet pet) {
        if (owner == null || pet == null || !supportsRest(pet.effectiveType())) {
            return false;
        }
        pet.clearCombat();
        pet.data().progress().put(COMFORT_REST_KEY, 1);
        pet.setState(PetState.IDLE);
        pet.showHint(restHint(pet.effectiveType()), 1_600L);
        pet.entity().ifPresent(entity -> {
            storeRestAnchor(pet.data(), entity.getLocation());
            PetCollisionSupport.applyOwnerExemption(owner, entity);
            applyRestPose(entity, pet.data(), pet.effectiveType(), true);
            faceOwner(entity, owner);
        });
        return true;
    }

    static boolean comfortRestRequested(OwnedPetData data) {
        return data != null && data.progress().getOrDefault(COMFORT_REST_KEY, 0) != 0;
    }

    static boolean keepResting(Player owner, LivingEntity entity, PetType type, OwnedPetData data, PetAbilityService abilityService) {
        if (!comfortRestRequested(data)) {
            return false;
        }
        if (owner == null || entity == null || entity.isDead() || !supportsRest(type) || data.isDown()) {
            clearRest(data);
            return false;
        }
        if (!entity.getWorld().equals(owner.getWorld())) {
            clearRest(data);
            return false;
        }
        Location anchor = restLocation(owner, entity, type, data);
        if (anchor.getWorld() == null || !anchor.getWorld().equals(owner.getWorld()) || anchor.distanceSquared(owner.getLocation()) > OWNER_REST_RADIUS_SQUARED) {
            clearRest(data);
            return false;
        }
        if (abilityService != null && abilityService.nearestEnemy(owner) != null) {
            clearRest(data);
            return false;
        }
        return true;
    }

    static void clearRest(OwnedPetData data) {
        if (data != null) {
            data.progress().remove(COMFORT_REST_KEY);
            data.progress().remove(COMFORT_REST_X_KEY);
            data.progress().remove(COMFORT_REST_Y_KEY);
            data.progress().remove(COMFORT_REST_Z_KEY);
        }
    }

    static boolean keepOwnerSpace(Player owner, LivingEntity entity, PetType type) {
        if (owner == null || entity == null || entity.isDead() || type.flying() || !entity.getWorld().equals(owner.getWorld())) {
            return false;
        }
        Location ownerLocation = owner.getLocation();
        Location entityLocation = entity.getLocation();
        if (Math.abs(entityLocation.getY() - ownerLocation.getY()) > 1.35D) {
            return false;
        }
        Vector away = entityLocation.toVector().subtract(ownerLocation.toVector()).setY(0.0D);
        if (away.lengthSquared() > OWNER_BUBBLE_RADIUS_SQUARED) {
            return false;
        }
        if (away.lengthSquared() < 0.01D) {
            away = ownerLocation.getDirection().clone().setY(0.0D).multiply(-1.0D);
        }
        if (away.lengthSquared() < 0.01D) {
            away = new Vector(0.0D, 0.0D, 1.0D);
        }
        Vector velocity = away.normalize().multiply(0.18D);
        velocity.setY(Math.min(0.12D, Math.max(-0.12D, entity.getVelocity().getY())));
        entity.setVelocity(velocity);
        return true;
    }

    static Location restLocation(Player owner, LivingEntity entity, PetType type, OwnedPetData data) {
        Location anchor = storedRestAnchor(owner, data);
        if (anchor != null) {
            return anchor;
        }
        if (entity != null && !entity.isDead()) {
            storeRestAnchor(data, entity.getLocation());
            return entity.getLocation().clone();
        }
        return fallbackRestLocation(owner, type, data);
    }

    private static Location fallbackRestLocation(Player owner, PetType type, OwnedPetData data) {
        Vector forward = PetMotionSupport.horizontalForward(owner);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        double sideSign = Math.floorMod(data.petId().hashCode(), 2) == 0 ? 1.0D : -1.0D;
        return owner.getLocation().clone()
            .add(forward.multiply(-1.25D))
            .add(side.multiply(sideSign * (type == PetType.PANDA ? 1.55D : 1.05D)));
    }

    private static void storeRestAnchor(OwnedPetData data, Location location) {
        if (data == null || location == null) {
            return;
        }
        data.progress().put(COMFORT_REST_X_KEY, scaledCoordinate(location.getX()));
        data.progress().put(COMFORT_REST_Y_KEY, scaledCoordinate(location.getY()));
        data.progress().put(COMFORT_REST_Z_KEY, scaledCoordinate(location.getZ()));
    }

    private static Location storedRestAnchor(Player owner, OwnedPetData data) {
        if (owner == null || data == null || owner.getWorld() == null) {
            return null;
        }
        if (!data.progress().containsKey(COMFORT_REST_X_KEY)
            || !data.progress().containsKey(COMFORT_REST_Y_KEY)
            || !data.progress().containsKey(COMFORT_REST_Z_KEY)) {
            return null;
        }
        return new Location(
            owner.getWorld(),
            unscaledCoordinate(data.progress().get(COMFORT_REST_X_KEY)),
            unscaledCoordinate(data.progress().get(COMFORT_REST_Y_KEY)),
            unscaledCoordinate(data.progress().get(COMFORT_REST_Z_KEY))
        );
    }

    private static int scaledCoordinate(double value) {
        double bounded = Math.max(Integer.MIN_VALUE / REST_COORDINATE_SCALE, Math.min(Integer.MAX_VALUE / REST_COORDINATE_SCALE, value));
        return (int) Math.round(bounded * REST_COORDINATE_SCALE);
    }

    private static double unscaledCoordinate(int value) {
        return value / REST_COORDINATE_SCALE;
    }

    static void applyRestPose(LivingEntity entity, OwnedPetData data, PetType type, boolean resting) {
        PetAppearanceSupport.applyVisualState(entity, data, resting);
        if (resting && type == PetType.PANDA) {
            invokeBoolean(entity, "setSitting", true);
        }
        if (resting && type == PetType.ARMADILLO) {
            invokeBoolean(entity, "setScrolled", true);
        }
        if (!resting && type == PetType.ARMADILLO) {
            invokeBoolean(entity, "setScrolled", false);
        }
    }

    static String restHint(PetType type) {
        return switch (type) {
            case CAT, WOLF -> GameText.text("pet.comfort.rest.sit", "садится рядом", "sits nearby");
            case FOX -> GameText.text("pet.comfort.rest.fox", "устраивается рядом", "settles nearby");
            case RABBIT, FROG, ARMADILLO -> GameText.text("pet.comfort.rest.small", "замирает рядом", "rests nearby");
            case PANDA -> GameText.text("pet.comfort.rest.panda", "отдыхает рядом", "rests nearby");
            default -> GameText.text("pet.comfort.rest.generic", "отдыхает рядом", "rests nearby");
        };
    }

    private static boolean supportsRest(PetType type) {
        return switch (type) {
            case CAT, WOLF, FOX, RABBIT, FROG, PANDA, ARMADILLO -> true;
            default -> false;
        };
    }

    private static void faceOwner(LivingEntity entity, Player owner) {
        Vector direction = owner.getEyeLocation().toVector().subtract(entity.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01D) {
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        entity.setRotation((float) yaw, 0.0F);
    }

    private static void invokeBoolean(LivingEntity entity, String methodName, boolean value) {
        try {
            Method method = entity.getClass().getMethod(methodName, boolean.class);
            method.invoke(entity, value);
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }
    }
}
