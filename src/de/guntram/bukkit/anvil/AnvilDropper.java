package de.guntram.bukkit.anvil;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class AnvilDropper implements Runnable {
    private final UUID playerUUID;
    private final String playerName;
    long ticksSinceInception;
    private final long ticksToThrowAnvil;
    private final long ticksToThawPlayer;
    private final long ticksToRemoveAnvil;
    private final Anvil plugin;
    private final String message;
    private boolean anvilThrown;
    private boolean playerThawed;
    private boolean anvilRemoved;
    private boolean waitingForFreeSpace;
    
    private Location playerPosition;
    private Location playerPositionLookingUp;
    
    private static String freezeMessage, throwMessage, thawMessage, removeMessage;
    private static int anvilHeight;
    private static boolean delayIfNoFreeSpace;
    
    public static void readConfigVars(Configuration config) {
            anvilHeight=config.getInt("anvilheight", 5);
            freezeMessage=config.getString("message.freeze");
            throwMessage =config.getString("message.anvil");
            thawMessage  =config.getString("message.thaw");
            removeMessage=config.getString("message.remove");
            delayIfNoFreeSpace=config.getBoolean("delayifnofreespace", false);
    }
    
    public String getMessage() { return message; }
    
    private boolean isSpaceAbove(Location location) {
        boolean freeSpace=true;
        World world=playerPosition.getWorld();
        for (int i=0; i<anvilHeight; i++) {
            if (world.getBlockAt(playerPosition.getBlockX(),
                                 playerPosition.getBlockY()+i,
                                 playerPosition.getBlockZ()).getType() != Material.AIR) {
                return false;
            }
        }
        
        Material below=
                world.getBlockAt(playerPosition.getBlockX(),
                                 playerPosition.getBlockY()-1,
                                 playerPosition.getBlockZ()).getType();
        if (!below.isOccluding())
            return false;
                
        return true;
    }
    
    public AnvilDropper(String playerName, Anvil plugin, Configuration config)
                                        throws CannotDropAnvilException {
        
        if (anvilHeight==0) {
            readConfigVars(config);
        }
        Player player=Bukkit.getPlayer(playerName);
        if (player==null) {
            throw new CannotDropAnvilException("No player with that name: "+playerName);
        }
        playerUUID=player.getUniqueId();
        this.playerName=playerName;
        ticksSinceInception=0;
        ticksToThrowAnvil=config.getInt("ticks.freezetoanvil", 100);
        ticksToThawPlayer=config.getInt("ticks.freezetothaw" , 200);
        ticksToRemoveAnvil=config.getInt("ticks.freezetoremove" , 150);
        anvilThrown=false;
        
        this.plugin=plugin;
        playerPosition=player.getLocation();

        if (!isSpaceAbove(playerPosition)) {
            if (delayIfNoFreeSpace) {
                waitingForFreeSpace=true;
                message="Will drop an anvil on "+playerName+" as soon as they move to air";
            } else {
                throw new CannotDropAnvilException("No free space above "+playerName);                
            }
        } else {
            message="Dropping an anvil on "+playerName;
            startAnvilSequence(player);
        }
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
    }
    
    private void startAnvilSequence(Player player) {
        playerPositionLookingUp=playerPosition.clone();
        playerPositionLookingUp.setPitch(-90);
        playerPositionLookingUp.setX((float)playerPositionLookingUp.getBlockX()+0.5);
        playerPositionLookingUp.setY((float)playerPositionLookingUp.getBlockY());
        playerPositionLookingUp.setZ((float)playerPositionLookingUp.getBlockZ()+0.5);
        if (freezeMessage!=null) 
            player.sendMessage(freezeMessage);
        plugin.addSpawnPreventLocation(playerPositionLookingUp);
        
    }
    
    @Override
    public void run() {
        Player player=Bukkit.getPlayer(playerUUID);
        if (player==null) {
            if (playerPositionLookingUp!=null)
                plugin.removeSpawnPreventLocation(playerPositionLookingUp);
            // If the player logged out, don't reschedule.
            return;
        }
        
        if (waitingForFreeSpace) {
            if (!isSpaceAbove(playerPosition=player.getLocation())) {
                // While the player is moving, don't retry on each tick - every 5th is sufficient
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 5);
                return;
            }
            waitingForFreeSpace=false;
            ticksSinceInception=0;
            startAnvilSequence(player);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
            return;
        }

        ticksSinceInception++;
        if (!anvilThrown && ticksSinceInception > ticksToThrowAnvil) {
            anvilThrown=true;
            ItemStack anvil=new ItemStack(Material.ANVIL, 1);
            Location anvilPos=playerPosition.clone().add(0, 5, 0);
            anvilPos.setPitch(0);
            anvilPos.setYaw(0);
            anvilPos.getWorld().getBlockAt(anvilPos).setType(Material.ANVIL);
            if (throwMessage!=null) 
                player.sendMessage(throwMessage);
        }
        if (!playerThawed && ticksSinceInception > ticksToThawPlayer) {
            playerThawed=true;
            if (thawMessage!=null)
                player.sendMessage(thawMessage);
            player.teleport(playerPosition);
        }
        if (!playerThawed) {
            player.teleport(playerPositionLookingUp);
        }
        
        if (!anvilRemoved && ticksSinceInception > ticksToRemoveAnvil) {
            anvilRemoved=true;
            if (playerPosition.getWorld().getBlockAt(playerPosition).getType()==Material.ANVIL) {
                playerPosition.getWorld().getBlockAt(playerPosition).setType(Material.AIR);
                if (removeMessage!=null)
                    player.sendMessage(removeMessage);
            }
        }

        if (!playerThawed || !anvilRemoved) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
        } else {
            plugin.removeSpawnPreventLocation(playerPositionLookingUp);
        }
    }
}
