package midnighttale.voidrespawn;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Voidrespawn.MODID)
public class Voidrespawn {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "voidrespawn";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    public Voidrespawn() {

    }
}
